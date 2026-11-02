package towerparse

import (
	"io"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

// Format is the outer content format selected by Parse.
type Format string

const (
	FormatYAML    Format = "yaml"
	FormatLink    Format = "link"
	FormatBase64  Format = "base64"
	FormatUnknown Format = "unknown"
)

// ProxyProvider is a proxy-providers entry. Fields preserves provider options
// such as interval, path, and health-check settings for the fetching slice.
type ProxyProvider struct {
	Name   string
	URL    string
	Fields map[string]any
}

// Stats describes parsing without retaining a set of all flight fingerprints.
type Stats struct {
	Candidates      int
	Parsed          int
	Skipped         int
	YAML            int
	Links           int
	Base64          int
	Base64Layers    int
	FormatCounts    map[string]int
	SkippedByReason map[string]int
	ProxyProviders  []ProxyProvider
}

func (s *Stats) init() {
	if s.FormatCounts == nil {
		s.FormatCounts = map[string]int{}
	}
	if s.SkippedByReason == nil {
		s.SkippedByReason = map[string]int{}
	}
}

func (s *Stats) skip(reason string) {
	s.init()
	s.Skipped++
	s.SkippedByReason[reason]++
}

func (s *Stats) provider(provider ProxyProvider) {
	s.ProxyProviders = append(s.ProxyProviders, provider)
}

// Parse reads a tower and emits each supported flight as soon as it is parsed.
func Parse(r io.Reader, emit func(flight.Flight) error) (Stats, error) {
	return parseReader(r, emit)
}
