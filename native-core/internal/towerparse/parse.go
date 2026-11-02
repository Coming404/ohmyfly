package towerparse

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"regexp"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"go.yaml.in/yaml/v3"
)

const (
	peekSize      = 64 * 1024
	maxLineLength = 4 * 1024 * 1024
	maxBase64Pass = 3
)

var linkScheme = regexp.MustCompile(`^([A-Za-z][A-Za-z0-9+.-]*):\/\/`)

func parseReader(source io.Reader, emit func(flight.Flight) error) (Stats, error) {
	stats := Stats{}
	reader, prefix, err := peekReader(source)
	if err != nil {
		return stats, err
	}
	format := DetectFormat(prefix)
	for format == FormatBase64 && stats.Base64Layers < maxBase64Pass {
		stats.Base64Layers++
		stats.Base64++
		reader, prefix, err = peekReader(newBase64StreamReader(reader))
		if err != nil {
			stats.skip("invalid base64")
			return stats, err
		}
		format = DetectFormat(prefix)
	}
	stats.init()
	if stats.Base64Layers > 0 {
		stats.FormatCounts[string(FormatBase64)] = stats.Base64Layers
	}
	if format == FormatBase64 {
		stats.skip("base64 depth exceeded")
		return stats, nil
	}
	stats.FormatCounts[string(format)]++
	switch format {
	case FormatYAML:
		stats.YAML++
		return parseYAML(reader, emit, stats)
	case FormatLink:
		stats.Links++
		return parseLinks(reader, emit, stats)
	default:
		stats.skip("unknown format")
		return stats, nil
	}
}

func peekReader(source io.Reader) (*bufio.Reader, []byte, error) {
	reader := bufio.NewReaderSize(source, peekSize)
	prefix, err := reader.Peek(peekSize)
	if err == bufio.ErrBufferFull {
		return reader, prefix, nil
	}
	if err != nil && err != io.EOF {
		return nil, nil, err
	}
	return reader, prefix, nil
}

func parseLinks(source io.Reader, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	scanner := bufio.NewScanner(source)
	scanner.Buffer(make([]byte, 64*1024), maxLineLength)
	line := 0
	for scanner.Scan() {
		line++
		text := strings.TrimSpace(scanner.Text())
		if text == "" || strings.HasPrefix(text, "#") {
			continue
		}
		stats.Candidates++
		match := linkScheme.FindStringSubmatch(text)
		if match == nil {
			stats.skip("malformed link")
			continue
		}
		node, fromBase64, supported, err := parseLink(text)
		protocol := strings.ToLower(match[1])
		if !supported {
			stats.skip("unsupported protocol: " + protocol)
			continue
		}
		if err != nil {
			stats.skip("invalid " + protocol + " link")
			continue
		}
		value, err := flight.Normalize(node)
		if err != nil {
			stats.skip("invalid " + protocol + " link")
			continue
		}
		value.SourceFormat = string(FormatLink)
		value.FromBase64 = fromBase64 || stats.Base64Layers > 0
		if err := emit(value); err != nil {
			return stats, err
		}
		stats.Parsed++
	}
	if err := scanner.Err(); err != nil {
		stats.skip(scanErrReason(err))
		return stats, fmt.Errorf("line %d: link input error", line+1)
	}
	return stats, nil
}

type yamlItem struct {
	start int
	lines []string
}

func parseYAML(source io.Reader, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	return scanYAML(source, emit, stats)
}

func leadingSpaces(line string) int {
	for index := 0; index < len(line); index++ {
		if line[index] != ' ' && line[index] != '\t' {
			return index
		}
	}
	return len(line)
}

func rootKey(line string) (string, string, bool) {
	key, value, ok := yamlMappingKey(line)
	return key, value, ok && (key == "proxies" || key == "proxy-providers")
}

func emitYAMLItem(item yamlItem, emit func(flight.Flight) error, stats *Stats) error {
	stats.Candidates++
	var value any
	if err := yaml.Unmarshal([]byte(strings.Join(item.lines, "\n")), &value); err != nil {
		stats.skip("malformed YAML item")
		return nil
	}
	record, ok := asMap(value)
	if !ok {
		stats.skip("malformed YAML item")
		return nil
	}
	flightValue, err := flight.Normalize(record)
	if err != nil {
		stats.skip("invalid YAML item")
		return nil
	}
	flightValue.SourceFormat = string(FormatYAML)
	if err := emit(flightValue); err != nil {
		return err
	}
	stats.Parsed++
	return nil
}

func scanErrReason(err error) string {
	if errors.Is(err, bufio.ErrTooLong) {
		return "line too long"
	}
	return "read error"
}

func asMap(value any) (map[string]any, bool) {
	switch record := value.(type) {
	case map[string]any:
		return record, true
	case map[any]any:
		out := make(map[string]any, len(record))
		for key, child := range record {
			name, ok := key.(string)
			if !ok {
				return nil, false
			}
			out[name] = child
		}
		return out, true
	case []any:
		if len(record) != 1 {
			return nil, false
		}
		return asMap(record[0])
	default:
		return nil, false
	}
}

func extractProvider(item yamlItem, stats *Stats) {
	value := map[string]any{}
	if err := yaml.Unmarshal([]byte(strings.Join(item.lines, "\n")), &value); err != nil {
		stats.skip("malformed proxy-provider")
		return
	}
	for name, raw := range value {
		fields, ok := asMap(raw)
		if !ok {
			stats.skip("malformed proxy-provider")
			continue
		}
		url, _ := fields["url"].(string)
		stats.provider(ProxyProvider{Name: name, URL: url, Fields: fields})
	}
}
