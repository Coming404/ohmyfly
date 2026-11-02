// Ported from SubBoost packages/core/src/parser/protocols/url-decode.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"testing"
)

func TestSubBoostURLDecodeHelpers(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  string
		err   bool
	}{
		// SubBoost packages/core/src/parser/protocols/url-decode.test.ts:6.
		{"standard base64", subBoostBase64("user:p@ss@[2001:db8::1]:443/path"), "user:p@ss@[2001:db8::1]:443/path", false},
		// SubBoost packages/core/src/parser/protocols/url-decode.test.ts:41.
		{"bad percent", "bad%", "", true},
		// SubBoost packages/core/src/parser/protocols/url-decode.test.ts:44.
		{"empty", "", "", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := decodeLinkBase64(tt.input)
			if tt.err {
				if err == nil {
					t.Fatal("decodeLinkBase64 succeeded")
				}
				return
			}
			if err != nil || string(got) != tt.want {
				t.Fatalf("decodeLinkBase64=%q, %v; want %q", got, err, tt.want)
			}
		})
	}

	// SubBoost packages/core/src/parser/protocols/url-decode.test.ts:18.
	t.Run("URL query and fragment decoding", func(t *testing.T) {
		parsed, query, err := linkURL("ssh://user:p%40ss@[2001:db8::1]:443/path/to?remarks=Node+One&empty=#Hash")
		if err != nil {
			t.Fatal(err)
		}
		if parsed.Hostname() != "2001:db8::1" || parsed.Port() != "443" || parsed.Path != "/path/to" || parsed.Fragment != "Hash" || query.Get("remarks") != "Node One" || query.Get("empty") != "" {
			t.Fatalf("unexpected URL: %#v query=%#v", parsed, query)
		}
		if linkName("Node+One", "") != "Node One" || linkName("", "fallback") != "fallback" {
			t.Fatal("linkName decoding mismatch")
		}
	})
}
