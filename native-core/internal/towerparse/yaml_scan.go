package towerparse

import (
	"bufio"
	"bytes"
	"fmt"
	"io"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"go.yaml.in/yaml/v3"
)

type yamlFlowState struct {
	depth   int
	quote   byte
	escaped bool
	plain   bool
}

func (s *yamlFlowState) add(line string) {
	for i := 0; i < len(line); i++ {
		c := line[i]
		if s.quote != 0 {
			if s.escaped {
				s.escaped = false
				continue
			}
			if s.quote == '"' && c == '\\' {
				s.escaped = true
				continue
			}
			if c == s.quote {
				if c == '\'' && i+1 < len(line) && line[i+1] == '\'' {
					i++
					continue
				}
				s.quote = 0
			}
			continue
		}
		if c == '#' && (i == 0 || line[i-1] == ' ' || line[i-1] == '\t') {
			break
		}
		switch c {
		case ' ', '\t', '\r', '\n':
			s.plain = false
		case '[', '{':
			s.depth++
			s.plain = false
		case ']', '}':
			s.depth--
			s.plain = false
		case ':', ',', '-':
			s.plain = false
		case '\'', '"':
			if !s.plain {
				s.quote = c
			}
		default:
			s.plain = true
		}
	}
	s.escaped = false
	s.plain = false
}

func yamlMappingKey(line string) (string, string, bool) {
	if line == "" || yamlSequenceLine(line) {
		return "", "", false
	}
	var quote byte
	for i := 0; i < len(line); i++ {
		c := line[i]
		if quote != 0 {
			if quote == '"' && c == '\\' {
				i++
				continue
			}
			if c == quote {
				if c == '\'' && i+1 < len(line) && line[i+1] == '\'' {
					i++
					continue
				}
				quote = 0
			}
			continue
		}
		if (c == '\'' || c == '"') && i == 0 {
			quote = c
			continue
		}
		if c != ':' {
			continue
		}
		key := strings.TrimSpace(line[:i])
		if key == "" {
			return "", "", false
		}
		if key[0] == '\'' || key[0] == '"' {
			var decoded string
			if yaml.Unmarshal([]byte(key), &decoded) != nil {
				return "", "", false
			}
			key = decoded
		}
		return key, line[i+1:], true
	}
	return "", "", false
}

func yamlSequenceLine(line string) bool {
	return line == "-" || strings.HasPrefix(line, "- ") || strings.HasPrefix(line, "-\t")
}

func yamlRecoverableNode(line string) bool {
	if !yamlSequenceLine(line) {
		return false
	}
	value := strings.TrimSpace(line[1:])
	return strings.HasPrefix(value, "{") || strings.HasPrefix(value, "name:")
}

func yamlDocumentMarker(line string) bool {
	for _, marker := range []string{"---", "..."} {
		if line == marker || strings.HasPrefix(line, marker+" ") || strings.HasPrefix(line, marker+"\t") {
			return true
		}
	}
	return false
}

func splitYAMLLines(data []byte, atEOF bool) (int, []byte, error) {
	if i := bytes.IndexAny(data, "\r\n"); i >= 0 {
		if data[i] == '\r' {
			if i+1 == len(data) && !atEOF {
				return 0, nil, nil
			}
			if i+1 < len(data) && data[i+1] == '\n' {
				return i + 2, data[:i], nil
			}
		}
		return i + 1, data[:i], nil
	}
	if atEOF && len(data) != 0 {
		return len(data), data, nil
	}
	return 0, nil, nil
}

type yamlScalarState struct {
	active  bool
	parent  int
	content int
}

func (s *yamlScalarState) contains(indent int, text string) bool {
	if !s.active {
		return false
	}
	if text == "" {
		return true
	}
	if s.content == 0 && indent > s.parent {
		s.content = indent
	}
	if s.content > 0 && indent >= s.content {
		return true
	}
	s.active = false
	return false
}

func yamlScalarStart(indent int, text string) yamlScalarState {
	if yamlSequenceLine(text) {
		text = strings.TrimSpace(text[1:])
		if _, _, ok := yamlMappingKey(text); ok {
			indent += 2
		}
	}
	if _, value, ok := yamlMappingKey(text); ok {
		text = strings.TrimSpace(value)
	}
	if text == "" || (text[0] != '|' && text[0] != '>') {
		return yamlScalarState{}
	}
	s := yamlScalarState{active: true, parent: indent}
	for i := 1; i < len(text); i++ {
		switch c := text[i]; {
		case c >= '1' && c <= '9':
			s.content = indent + int(c-'0')
		case c == '+' || c == '-':
		case c == ' ' || c == '\t' || c == '#':
			return s
		default:
			return yamlScalarState{}
		}
	}
	return s
}

type yamlScanItem struct {
	yamlItem
	size    int
	reason  string
	flow    yamlFlowState
	parents []int
}

func (item *yamlScanItem) add(raw string) {
	item.size += len(raw) + 1
	if item.size > maxLineLength && item.reason == "" {
		item.reason = "yaml item too large"
	}
	if item.reason == "" {
		item.lines = append(item.lines, raw)
	}
	item.flow.add(raw)
}

func (item *yamlScanItem) nesting(indent int, text string) {
	sequence := yamlSequenceLine(text)
	for len(item.parents) > 0 {
		last := item.parents[len(item.parents)-1]
		if indent > last || (sequence && indent == last) {
			break
		}
		item.parents = item.parents[:len(item.parents)-1]
	}
	if sequence {
		text = strings.TrimSpace(text[1:])
		indent += 2
	}
	if _, value, ok := yamlMappingKey(text); ok && yamlEmptyValue(value) {
		item.parents = append(item.parents, indent)
	}
}

func yamlEmptyValue(value string) bool {
	value = strings.TrimSpace(value)
	return value == "" || strings.HasPrefix(value, "#")
}

func yamlAliasReason(item yamlItem) string {
	for _, line := range item.lines {
		if strings.Contains(line, "*") {
			var node yaml.Node
			if err := yaml.Unmarshal([]byte(strings.Join(item.lines, "\n")), &node); err != nil && strings.Contains(err.Error(), "unknown anchor") {
				return "yaml cross-item alias unsupported"
			}
			break
		}
	}
	return ""
}

// yamlItemWithFinalNewline keeps the line break that ended the item's last
// physical line, so clip-chomped block scalars match whole-document parsing.
func yamlItemWithFinalNewline(item yamlItem) yamlItem {
	n := len(item.lines)
	if n == 0 || item.lines[n-1] == "" || strings.HasSuffix(item.lines[n-1], "\n") {
		return item
	}
	item.lines = append(item.lines[:n:n], "")
	return item
}

func emitScannedYAML(item *yamlScanItem, section string, inline bool, emit func(flight.Flight) error, stats *Stats) error {
	reason := item.reason
	if reason == "" {
		reason = yamlAliasReason(item.yamlItem)
	}
	if reason != "" {
		if section == "proxies" {
			stats.Candidates++
		}
		stats.skip(reason)
		return nil
	}
	if !inline {
		parsed := yamlItemWithFinalNewline(item.yamlItem)
		if section == "proxies" {
			return emitYAMLItem(parsed, emit, stats)
		}
		extractProvider(parsed, stats)
		return nil
	}
	var document yaml.Node
	if err := yaml.Unmarshal([]byte(strings.Join(item.lines, "\n")), &document); err != nil || len(document.Content) == 0 {
		stats.skip("malformed YAML flow collection")
		return nil
	}
	collection := document.Content[0]
	if collection.Tag == "!!null" {
		return nil
	}
	if section == "proxies" && collection.Kind == yaml.SequenceNode {
		for _, node := range collection.Content {
			data, err := yaml.Marshal(node)
			if err != nil {
				return err
			}
			if err := emitScannedYAML(&yamlScanItem{yamlItem: yamlItem{start: item.start, lines: []string{string(data)}}}, section, false, emit, stats); err != nil {
				return err
			}
		}
		return nil
	}
	if section == "proxy-providers" && collection.Kind == yaml.MappingNode {
		for i := 0; i < len(collection.Content); i += 2 {
			node := &yaml.Node{Kind: yaml.MappingNode, Content: collection.Content[i : i+2]}
			data, err := yaml.Marshal(node)
			if err != nil {
				return err
			}
			if err := emitScannedYAML(&yamlScanItem{yamlItem: yamlItem{start: item.start, lines: []string{string(data)}}}, section, false, emit, stats); err != nil {
				return err
			}
		}
		return nil
	}
	stats.skip("malformed YAML flow collection")
	return nil
}

func scanYAML(source io.Reader, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	scanner := bufio.NewScanner(source)
	scanner.Buffer(make([]byte, 64*1024), maxLineLength)
	scanner.Split(splitYAMLLines)
	rootIndent, itemIndent := -1, -1
	section := ""
	inline := false
	seenProxies, seenProviders := false, false
	documentStarted := false
	var item *yamlScanItem
	var scalar yamlScalarState
	line := 0
	flush := func() error {
		if item == nil {
			return nil
		}
		current := item
		item = nil
		return emitScannedYAML(current, section, inline, emit, &stats)
	}
	start := func(raw string) {
		item = &yamlScanItem{yamlItem: yamlItem{start: line}}
		item.add(raw)
	}
	for scanner.Scan() {
		line++
		raw := scanner.Text()
		if line == 1 {
			raw = strings.TrimPrefix(raw, "\ufeff")
		}
		text := strings.TrimSpace(raw)
		indent := leadingSpaces(raw)
		if scalar.contains(indent, text) {
			if item != nil {
				item.add(raw)
				item.flow = yamlFlowState{}
			}
			continue
		}
		if text == "" || strings.HasPrefix(text, "#") {
			if item != nil {
				item.add(raw)
			}
			continue
		}
		key, value, target := rootKey(text)
		mapping := key != ""
		sequence := yamlSequenceLine(text)
		tab := strings.Contains(raw[:indent], "\t")
		marker := indent == 0 && yamlDocumentMarker(text)
		if item != nil && (inline || item.flow.depth > 0 || item.flow.quote != 0) {
			boundary := item.flow.quote == 0 && (marker || (!inline && indent <= itemIndent && yamlRecoverableNode(text)))
			if !boundary {
				item.add(raw)
				if inline && item.flow.depth <= 0 && item.flow.quote == 0 {
					if err := flush(); err != nil {
						return stats, err
					}
					section, inline = "", false
				}
				continue
			}
			if err := flush(); err != nil {
				return stats, err
			}
			if inline {
				section, inline = "", false
			}
		}
		if marker {
			if err := flush(); err != nil {
				return stats, err
			}
			// Match yaml.Unmarshal, which returns only the first document.
			if documentStarted || strings.HasPrefix(text, "...") {
				return stats, nil
			}
			documentStarted = true
			continue
		}
		documentStarted = true
		if tab && section != "" {
			newItem := sequence || (section == "proxy-providers" && mapping && indent <= itemIndent)
			if newItem {
				if err := flush(); err != nil {
					return stats, err
				}
				start(raw)
			}
			if item != nil {
				item.reason = "yaml tab indentation"
			} else {
				stats.skip("yaml tab indentation")
			}
			continue
		}
		if !tab && mapping && (rootIndent < 0 || indent <= rootIndent) {
			if err := flush(); err != nil {
				return stats, err
			}
			rootIndent, itemIndent = indent, -1
			section, inline = "", false
			if target {
				duplicate := (key == "proxies" && seenProxies) || (key == "proxy-providers" && seenProviders)
				if duplicate {
					stats.skip("yaml duplicate root key: " + key)
				}
				if key == "proxies" {
					seenProxies = true
				} else {
					seenProviders = true
				}
				section = key
				if !yamlEmptyValue(value) {
					inline = true
					start(strings.TrimSpace(value))
					if item.flow.depth <= 0 && item.flow.quote == 0 {
						if err := flush(); err != nil {
							return stats, err
						}
						section, inline = "", false
					}
				}
			}
			scalar = yamlScalarStart(indent, text)
			continue
		}
		if section == "proxies" {
			if sequence && indent >= rootIndent {
				if itemIndent < 0 {
					itemIndent = indent
				}
				nested := item != nil && len(item.parents) > 0 && indent >= item.parents[len(item.parents)-1]
				if indent == itemIndent || !nested {
					if err := flush(); err != nil {
						return stats, err
					}
					if indent != itemIndent {
						if !yamlRecoverableNode(text) {
							stats.Candidates++
							stats.skip("yaml ambiguous list indentation")
							continue
						}
						stats.skip("yaml inconsistent list indentation")
					}
					start(raw)
				} else {
					item.add(raw)
				}
			} else if indent <= rootIndent {
				if err := flush(); err != nil {
					return stats, err
				}
				section = ""
			} else if item != nil {
				item.add(raw)
			}
		} else if section == "proxy-providers" {
			if indent <= rootIndent {
				if err := flush(); err != nil {
					return stats, err
				}
				section = ""
			} else if mapping && (itemIndent < 0 || indent <= itemIndent) {
				if err := flush(); err != nil {
					return stats, err
				}
				if itemIndent >= 0 && indent != itemIndent {
					stats.skip("yaml inconsistent provider indentation")
				}
				if itemIndent < 0 {
					itemIndent = indent
				}
				start(raw)
			} else if item != nil {
				item.add(raw)
			}
		}
		scalar = yamlScalarStart(indent, text)
		if item != nil && item.reason == "" && item.flow.depth == 0 && item.flow.quote == 0 {
			item.nesting(indent, text)
		}
	}
	if err := flush(); err != nil {
		return stats, err
	}
	if err := scanner.Err(); err != nil {
		stats.skip(scanErrReason(err))
		return stats, fmt.Errorf("line %d: YAML input error", line+1)
	}
	return stats, nil
}
