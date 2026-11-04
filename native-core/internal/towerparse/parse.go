package towerparse

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"math"
	"regexp"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
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
		text := jsTrimSpace(scanner.Text())
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

// parseYAML loads the whole document like upstream parseClashYaml. Only when
// the document cannot be loaded at all, or exceeds maxYAMLDocumentBytes, does
// it fall back to the item scanner, which skips just the broken items.
func parseYAML(source io.Reader, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	data, err := io.ReadAll(io.LimitReader(source, maxYAMLDocumentBytes+1))
	if err != nil {
		stats.skip(scanErrReason(err))
		return stats, err
	}
	if len(data) > maxYAMLDocumentBytes {
		return scanYAML(io.MultiReader(bytes.NewReader(data), source), emit, stats)
	}
	value, err := loadClashYAML(string(data))
	if err != nil {
		return scanYAML(bytes.NewReader(data), emit, stats)
	}
	return emitYAMLDocument(value, emit, stats)
}

// emitYAMLDocument mirrors the three root shapes upstream accepts: a map with a
// proxies array, a map that is itself one node, and a top-level node list.
func emitYAMLDocument(value any, emit func(flight.Flight) error, stats Stats) (Stats, error) {
	if items, ok := value.([]any); ok {
		for _, item := range items {
			if !isYAMLCollection(item) {
				continue
			}
			if err := emitYAMLValue(item, emit, &stats); err != nil {
				return stats, err
			}
		}
		return stats, nil
	}
	root, ok := yamlMapping(value)
	if !ok {
		if isFalsyYAML(value) {
			stats.skip("empty YAML")
		} else {
			stats.skip("unrecognized YAML")
		}
		return stats, nil
	}
	if proxies, ok := root["proxies"].([]any); ok {
		for _, item := range proxies {
			if err := emitYAMLValue(item, emit, &stats); err != nil {
				return stats, err
			}
		}
	} else if err := emitYAMLValue(root, emit, &stats); err != nil {
		return stats, err
	}
	if providers, ok := yamlMapping(root["proxy-providers"]); ok {
		collectProviders(providers, &stats)
	}
	return stats, nil
}

func emitYAMLValue(value any, emit func(flight.Flight) error, stats *Stats) error {
	stats.Candidates++
	record, ok := yamlMapping(value)
	if !ok {
		stats.skip("malformed YAML item")
		return nil
	}
	return emitYAMLRecord(record, emit, stats)
}

func emitYAMLRecord(record map[string]any, emit func(flight.Flight) error, stats *Stats) error {
	normalizeYAMLShadowsocks(record)
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

func isYAMLCollection(value any) bool {
	switch value.(type) {
	case map[string]any, map[any]any, []any:
		return true
	}
	return false
}

// isFalsyYAML matches the JS falsy roots upstream reports as an empty config.
func isFalsyYAML(value any) bool {
	switch v := value.(type) {
	case nil:
		return true
	case bool:
		return !v
	case int:
		return v == 0
	case int64:
		return v == 0
	case uint64:
		return v == 0
	case float64:
		return v == 0 || math.IsNaN(v)
	case string:
		return v == ""
	}
	return false
}

func yamlMapping(value any) (map[string]any, bool) {
	switch value.(type) {
	case map[string]any, map[any]any:
		return asMap(value)
	}
	return nil, false
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

// emitYAMLItem handles one item from the fallback scanner with the same
// scalar rules as the whole-document path.
func emitYAMLItem(item yamlItem, emit func(flight.Flight) error, stats *Stats) error {
	stats.Candidates++
	value, err := decodeSingleYAML(strings.Join(item.lines, "\n"))
	if err != nil {
		stats.skip("malformed YAML item")
		return nil
	}
	record, ok := asMap(value)
	if !ok {
		stats.skip("malformed YAML item")
		return nil
	}
	return emitYAMLRecord(record, emit, stats)
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
	value, err := decodeSingleYAML(strings.Join(item.lines, "\n"))
	if err == nil && value == nil {
		return
	}
	providers, ok := yamlMapping(value)
	if err != nil || !ok {
		stats.skip("malformed proxy-provider")
		return
	}
	collectProviders(providers, stats)
}

func collectProviders(providers map[string]any, stats *Stats) {
	for name, raw := range providers {
		fields, ok := asMap(raw)
		if !ok {
			stats.skip("malformed proxy-provider")
			continue
		}
		url, _ := fields["url"].(string)
		stats.provider(ProxyProvider{Name: name, URL: url, Fields: fields})
	}
}
