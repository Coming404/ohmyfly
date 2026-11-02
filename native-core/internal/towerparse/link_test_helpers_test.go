package towerparse

import (
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func parseTestLink(t *testing.T, link string) flight.Flight {
	t.Helper()
	var parsed []flight.Flight
	stats, err := Parse(strings.NewReader(link), func(value flight.Flight) error {
		parsed = append(parsed, value)
		return nil
	})
	if err != nil || stats.Parsed != 1 || len(parsed) != 1 {
		t.Fatalf("parse %q: err=%v stats=%+v parsed=%+v", link, err, stats, parsed)
	}
	return parsed[0]
}

func assertMapAbsent(t *testing.T, got map[string]any, fields ...string) {
	t.Helper()
	for _, field := range fields {
		if _, exists := got[field]; exists {
			t.Errorf("unexpected field %q", field)
		}
	}
}
