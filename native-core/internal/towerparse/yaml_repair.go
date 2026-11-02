package towerparse

import (
	"regexp"
	"strings"
	"unicode"
)

// Ports of upstream clash-yaml.ts and yaml-flow-indent.ts repairs. They work on
// bytes; only ASCII whitespace counts as JS \s in the flow scanner.

var (
	inlineNameItem   = regexp.MustCompile(`^(\s*)-\s*(name\s*:\s*.+)$`)
	sequencePrefix   = regexp.MustCompile(`^ *(?:- +)+`)
	flowBoundaryChar = "[{,"
)

func splitRepairLines(input string) []string {
	lines := strings.Split(input, "\n")
	for i, line := range lines {
		lines[i] = strings.TrimSuffix(line, "\r")
	}
	return lines
}

func countLeadingSpaces(line string) int {
	count := 0
	for count < len(line) && line[count] == ' ' {
		count++
	}
	return count
}

// repairInlineListIndent fixes "- name: x" items whose following keys sit deeper
// than the inline key by moving the inline key onto its own line.
func repairInlineListIndent(input string) string {
	lines := splitRepairLines(input)
	out := make([]string, 0, len(lines))
	for index := 0; index < len(lines); {
		line := lines[index]
		match := inlineNameItem.FindStringSubmatch(line)
		if match == nil {
			out = append(out, line)
			index++
			continue
		}
		dashIndent := len(match[1])
		minIndent := -1
		blockEnd := index + 1
		for ; blockEnd < len(lines); blockEnd++ {
			trimmed := strings.TrimSpace(lines[blockEnd])
			if trimmed == "" || strings.HasPrefix(trimmed, "#") {
				continue
			}
			indent := countLeadingSpaces(lines[blockEnd])
			if indent <= dashIndent {
				break
			}
			if minIndent < 0 || indent < minIndent {
				minIndent = indent
			}
		}
		if minIndent > dashIndent+2 {
			out = append(out, strings.Repeat(" ", dashIndent)+"-", strings.Repeat(" ", minIndent)+match[2])
		} else {
			out = append(out, line)
		}
		out = append(out, lines[index+1:blockEnd]...)
		index = blockEnd
	}
	return strings.Join(out, "\n")
}

func isRootProxiesKey(trimmed string) bool {
	rest, ok := strings.CutPrefix(trimmed, "proxies:")
	return ok && (rest == "" || strings.HasPrefix(strings.TrimLeftFunc(rest, unicode.IsSpace), "#"))
}

func isSingleLineFlowSequenceItem(trimmed string) bool {
	if !strings.HasPrefix(trimmed, "- {") {
		return false
	}
	closing := strings.LastIndexByte(trimmed, '}')
	if closing < 3 {
		return false
	}
	trailing := strings.TrimSpace(trimmed[closing+1:])
	return trailing == "" || strings.HasPrefix(trailing, "#")
}

// repairRootFlowProxyListIndent aligns a root proxies list made only of
// single-line flow items when their indents disagree.
func repairRootFlowProxyListIndent(input string) string {
	lines := splitRepairLines(input)
	rootIndent := -1
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || trimmed[0] == '#' || trimmed[0] == '-' || strings.IndexByte(trimmed, ':') <= 0 {
			continue
		}
		if indent := countLeadingSpaces(line); rootIndent < 0 || indent < rootIndent {
			rootIndent = indent
		}
	}
	if rootIndent < 0 {
		return input
	}
	proxiesLine := -1
	for i, line := range lines {
		if countLeadingSpaces(line) == rootIndent && isRootProxiesKey(strings.TrimSpace(line)) {
			proxiesLine = i
			break
		}
	}
	if proxiesLine < 0 {
		return input
	}
	var items []int
	for i := proxiesLine + 1; i < len(lines); i++ {
		trimmed := strings.TrimSpace(lines[i])
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		indent := countLeadingSpaces(lines[i])
		if !strings.HasPrefix(trimmed, "-") && indent <= rootIndent {
			break
		}
		if indent < rootIndent || !isSingleLineFlowSequenceItem(trimmed) {
			return input
		}
		items = append(items, i)
	}
	if len(items) < 2 {
		return input
	}
	canonical := countLeadingSpaces(lines[items[0]])
	aligned := true
	for _, i := range items {
		aligned = aligned && countLeadingSpaces(lines[i]) == canonical
	}
	if aligned {
		return input
	}
	padding := strings.Repeat(" ", canonical)
	for _, i := range items {
		lines[i] = padding + strings.TrimLeftFunc(lines[i], unicode.IsSpace)
	}
	return strings.Join(lines, "\n")
}

// splitKeepingLineEnds splits after "\n" and after a lone "\r".
func splitKeepingLineEnds(content string) []string {
	var lines []string
	start := 0
	for i := 0; i < len(content); i++ {
		if content[i] == '\n' || (content[i] == '\r' && (i+1 == len(content) || content[i+1] != '\n')) {
			lines = append(lines, content[start:i+1])
			start = i + 1
		}
	}
	if start < len(content) || len(lines) == 0 {
		lines = append(lines, content[start:])
	}
	return lines
}

func isASCIISpace(ch byte) bool {
	return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f' || ch == '\v'
}

func isBlockScalarHeader(line string, position int) bool {
	cursor := position + 1
	for n := 0; n < 2 && cursor < len(line) && strings.IndexByte("123456789+-", line[cursor]) >= 0; n++ {
		cursor++
	}
	for cursor < len(line) && (line[cursor] == ' ' || line[cursor] == '\t') {
		cursor++
	}
	return cursor == len(line) || strings.IndexByte("\r\n#", line[cursor]) >= 0
}

// normalizeLegacyFlowIndent restores legacy flow whitespace only at
// unambiguous node boundaries. It returns content unchanged when unsure.
func normalizeLegacyFlowIndent(content string) string {
	lines := splitKeepingLineEnds(content)
	s := flowIndentScanner{nodeStart: true, blockIndent: -1, plainIndent: -1}
	added := 0
	for index, line := range lines {
		indent := countLeadingSpaces(line)
		first := line[indent:]
		blank := strings.TrimSpace(first) == ""
		if s.plainIndent >= 0 {
			if blank || strings.HasPrefix(first, "#") || indent > s.plainIndent {
				continue
			}
			s.plainIndent = -1
		}
		if s.blockIndent >= 0 {
			if blank || indent > s.blockIndent {
				continue
			}
			s.blockIndent = -1
		}
		if len(s.flow) > 0 && s.quote == 0 && !blank && !strings.HasPrefix(first, "#") {
			if isDocumentMarker(first) {
				return content
			}
			boundary := (s.previous != 0 && strings.IndexByte(flowBoundaryChar, s.previous) >= 0) ||
				strings.IndexByte(",]}", first[0]) >= 0
			required := s.flow[len(s.flow)-1]
			if boundary && indent < required && first[0] != '\t' {
				added += required - indent
				// Keep the repair linear in input size even for adversarial indentation.
				if added > len(content) {
					return content
				}
				line = strings.Repeat(" ", required-indent) + line
				lines[index] = line
			}
		}
		if !s.scanLine(line, indent) {
			return content
		}
	}
	return strings.Join(lines, "")
}

func isDocumentMarker(first string) bool {
	if !strings.HasPrefix(first, "---") && !strings.HasPrefix(first, "...") {
		return false
	}
	return len(first) == 3 || isASCIISpace(first[3])
}

type flowIndentScanner struct {
	flow        []int
	quote       byte
	nodeStart   bool
	previous    byte
	blockIndent int
	plainIndent int
}

// scanLine tracks quotes, flow nesting and node starts for one line. indent is
// the line's indent before any padding, as upstream computes it.
func (s *flowIndentScanner) scanLine(line string, indent int) bool {
	sequenceIndent := indent
	if prefix := sequencePrefix.FindString(line); prefix != "" {
		sequenceIndent = len(prefix)
	}
	blockMapping := false
	nested := func() int {
		if sequenceIndent <= indent {
			return indent
		}
		if blockMapping {
			return sequenceIndent
		}
		return sequenceIndent - 2
	}
	next := func(i int) byte {
		if i+1 < len(line) {
			return line[i+1]
		}
		return '\n'
	}
	for i := 0; i < len(line); i++ {
		ch := line[i]
		if s.quote != 0 {
			i = s.scanQuoted(line, i)
			continue
		}
		if isASCIISpace(ch) {
			continue
		}
		switch {
		case ch == '#' && (i == 0 || strings.IndexByte(" \t\n\r\f\v[{,", line[i-1]) >= 0):
			return true
		case s.nodeStart && (ch == '"' || ch == '\''):
			s.quote, s.nodeStart = ch, false
		case s.nodeStart && len(s.flow) == 0 && (ch == '|' || ch == '>') && isBlockScalarHeader(line, i):
			s.blockIndent = nested()
			return true
		case s.nodeStart && (ch == '!' || ch == '&'):
			end, ok := skipTagOrAnchor(line, i)
			if !ok {
				return false
			}
			i = end
		case s.nodeStart && (ch == '[' || ch == '{'):
			s.plainIndent = -1
			parent := 0
			if len(s.flow) > 0 {
				parent = s.flow[len(s.flow)-1]
			}
			s.flow = append(s.flow, max(indent+1, parent))
			s.previous = ch
		case len(s.flow) > 0 && (ch == ']' || ch == '}'):
			s.flow = s.flow[:len(s.flow)-1]
			s.nodeStart, s.previous = false, ch
		case (len(s.flow) > 0 && ch == ',') || (ch == ':' && (isASCIISpace(next(i)) || strings.IndexByte("[]{},", next(i)) >= 0)):
			if len(s.flow) == 0 {
				blockMapping = true
			}
			s.plainIndent = -1
			s.nodeStart, s.previous = true, ch
		case s.nodeStart && (ch == '-' || ch == '?') && isASCIISpace(next(i)):
			s.previous = ch
		default:
			if s.nodeStart && len(s.flow) == 0 {
				s.plainIndent = nested()
			}
			s.nodeStart, s.previous = false, ch
		}
	}
	if len(s.flow) == 0 && s.quote == 0 {
		s.nodeStart, s.previous = true, 0
	}
	return true
}

// skipTagOrAnchor returns the index of the last byte of a tag or anchor
// starting at i; a verbatim tag "!<" without ">" is unrecoverable.
// scanQuoted consumes one byte inside a quoted scalar and returns the index of the last byte it used.
func (s *flowIndentScanner) scanQuoted(line string, i int) int {
	ch := line[i]
	switch {
	case s.quote == '"' && ch == '\\':
		return i + 1
	case ch != s.quote:
		return i
	case s.quote == '\'' && i+1 < len(line) && line[i+1] == '\'':
		return i + 1
	}
	s.quote, s.previous = 0, ch
	return i
}

func skipTagOrAnchor(line string, i int) (int, bool) {
	if line[i] == '!' && i+1 < len(line) && line[i+1] == '<' {
		end := strings.IndexByte(line[i+2:], '>')
		if end < 0 {
			return 0, false
		}
		return i + 2 + end, true
	}
	for i+1 < len(line) && !isASCIISpace(line[i+1]) && strings.IndexByte("[]{},", line[i+1]) < 0 {
		i++
	}
	return i, true
}
