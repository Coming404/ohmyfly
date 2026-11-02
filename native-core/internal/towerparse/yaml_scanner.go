package towerparse

import (
	"bufio"
	"fmt"
	"io"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

// yamlScanner holds the line-by-line state of the fallback scanner used when whole-document loading fails.
type yamlScanner struct {
	emit                       func(flight.Flight) error
	stats                      Stats
	rootIndent, itemIndent     int
	section                    string
	inline                     bool
	seenProxies, seenProviders bool
	documentStarted            bool
	item                       *yamlScanItem
	scalar                     yamlScalarState
	line                       int
}

func scanYAML(source io.Reader, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	scanner := bufio.NewScanner(source)
	scanner.Buffer(make([]byte, 64*1024), maxLineLength)
	scanner.Split(splitYAMLLines)
	s := &yamlScanner{emit: emit, stats: stats, rootIndent: -1, itemIndent: -1}
	for scanner.Scan() {
		s.line++
		raw := scanner.Text()
		if s.line == 1 {
			raw = strings.TrimPrefix(raw, "\ufeff")
		}
		done, err := s.scanLine(raw)
		if err != nil {
			return s.stats, err
		}
		if done {
			return s.stats, nil
		}
	}
	if err := s.flush(); err != nil {
		return s.stats, err
	}
	if err := scanner.Err(); err != nil {
		s.stats.skip(scanErrReason(err))
		return s.stats, fmt.Errorf("line %d: YAML input error", s.line+1)
	}
	return s.stats, nil
}

func (s *yamlScanner) flush() error {
	if s.item == nil {
		return nil
	}
	current := s.item
	s.item = nil
	return emitScannedYAML(current, s.section, s.inline, s.emit, &s.stats)
}

func (s *yamlScanner) start(raw string) {
	s.item = &yamlScanItem{yamlItem: yamlItem{start: s.line}}
	s.item.add(raw)
}

// scanLine handles one input line and reports done once a second document begins.
func (s *yamlScanner) scanLine(raw string) (bool, error) {
	text := strings.TrimSpace(raw)
	indent := leadingSpaces(raw)
	if s.scalar.contains(indent, text) {
		if s.item != nil {
			s.item.add(raw)
			s.item.flow = yamlFlowState{}
		}
		return false, nil
	}
	if text == "" || strings.HasPrefix(text, "#") {
		if s.item != nil {
			s.item.add(raw)
		}
		return false, nil
	}
	key, value, target := rootKey(text)
	mapping := key != ""
	sequence := yamlSequenceLine(text)
	tab := strings.Contains(raw[:indent], "\t")
	marker := indent == 0 && yamlDocumentMarker(text)
	if s.item != nil && (s.inline || s.item.flow.depth > 0 || s.item.flow.quote != 0) {
		if consumed, err := s.continueFlow(raw, text, indent, marker); consumed || err != nil {
			return false, err
		}
	}
	if marker {
		if err := s.flush(); err != nil {
			return false, err
		}
		// Match yaml.Unmarshal, which returns only the first document.
		if s.documentStarted || strings.HasPrefix(text, "...") {
			return true, nil
		}
		s.documentStarted = true
		return false, nil
	}
	s.documentStarted = true
	if tab && s.section != "" {
		return false, s.tabLine(raw, indent, mapping, sequence)
	}
	if !tab && mapping && (s.rootIndent < 0 || indent <= s.rootIndent) {
		return false, s.rootLine(text, indent, key, value, target)
	}
	switch s.section {
	case "proxies":
		if skipTail, err := s.proxiesLine(raw, text, indent, sequence); skipTail || err != nil {
			return false, err
		}
	case "proxy-providers":
		if err := s.providersLine(raw, indent, mapping); err != nil {
			return false, err
		}
	}
	s.scalar = yamlScalarStart(indent, text)
	if s.item != nil && s.item.reason == "" && s.item.flow.depth == 0 && s.item.flow.quote == 0 {
		s.item.nesting(indent, text)
	}
	return false, nil
}

// continueFlow appends a line to an open flow or inline item and reports whether the line was consumed.
func (s *yamlScanner) continueFlow(raw, text string, indent int, marker bool) (bool, error) {
	boundary := s.item.flow.quote == 0 && (marker || (!s.inline && indent <= s.itemIndent && yamlRecoverableNode(text)))
	if !boundary {
		s.item.add(raw)
		if s.inline && s.item.flow.depth <= 0 && s.item.flow.quote == 0 {
			if err := s.flush(); err != nil {
				return true, err
			}
			s.section, s.inline = "", false
		}
		return true, nil
	}
	if err := s.flush(); err != nil {
		return true, err
	}
	if s.inline {
		s.section, s.inline = "", false
	}
	return false, nil
}

func (s *yamlScanner) tabLine(raw string, indent int, mapping, sequence bool) error {
	if sequence || (s.section == "proxy-providers" && mapping && indent <= s.itemIndent) {
		if err := s.flush(); err != nil {
			return err
		}
		s.start(raw)
	}
	if s.item != nil {
		s.item.reason = "yaml tab indentation"
	} else {
		s.stats.skip("yaml tab indentation")
	}
	return nil
}

func (s *yamlScanner) rootLine(text string, indent int, key, value string, target bool) error {
	if err := s.flush(); err != nil {
		return err
	}
	s.rootIndent, s.itemIndent = indent, -1
	s.section, s.inline = "", false
	if target {
		duplicate := (key == "proxies" && s.seenProxies) || (key == "proxy-providers" && s.seenProviders)
		if duplicate {
			s.stats.skip("yaml duplicate root key: " + key)
		}
		if key == "proxies" {
			s.seenProxies = true
		} else {
			s.seenProviders = true
		}
		s.section = key
		if !yamlEmptyValue(value) {
			s.inline = true
			s.start(strings.TrimSpace(value))
			if s.item.flow.depth <= 0 && s.item.flow.quote == 0 {
				if err := s.flush(); err != nil {
					return err
				}
				s.section, s.inline = "", false
			}
		}
	}
	s.scalar = yamlScalarStart(indent, text)
	return nil
}

// proxiesLine reports skipTail when the line was dropped before the scalar and nesting state update.
func (s *yamlScanner) proxiesLine(raw, text string, indent int, sequence bool) (bool, error) {
	if sequence && indent >= s.rootIndent {
		if s.itemIndent < 0 {
			s.itemIndent = indent
		}
		nested := s.item != nil && len(s.item.parents) > 0 && indent >= s.item.parents[len(s.item.parents)-1]
		if indent != s.itemIndent && nested {
			s.item.add(raw)
			return false, nil
		}
		if err := s.flush(); err != nil {
			return false, err
		}
		if indent != s.itemIndent {
			if !yamlRecoverableNode(text) {
				s.stats.Candidates++
				s.stats.skip("yaml ambiguous list indentation")
				return true, nil
			}
			s.stats.skip("yaml inconsistent list indentation")
		}
		s.start(raw)
	} else if indent <= s.rootIndent {
		if err := s.flush(); err != nil {
			return false, err
		}
		s.section = ""
	} else if s.item != nil {
		s.item.add(raw)
	}
	return false, nil
}

func (s *yamlScanner) providersLine(raw string, indent int, mapping bool) error {
	if indent <= s.rootIndent {
		if err := s.flush(); err != nil {
			return err
		}
		s.section = ""
	} else if mapping && (s.itemIndent < 0 || indent <= s.itemIndent) {
		if err := s.flush(); err != nil {
			return err
		}
		if s.itemIndent >= 0 && indent != s.itemIndent {
			s.stats.skip("yaml inconsistent provider indentation")
		}
		if s.itemIndent < 0 {
			s.itemIndent = indent
		}
		s.start(raw)
	} else if s.item != nil {
		s.item.add(raw)
	}
	return nil
}
