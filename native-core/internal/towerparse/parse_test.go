package towerparse

import (
	"encoding/base64"
	"io"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func TestDetectFormat(t *testing.T) {
	cases := []struct {
		input  string
		format Format
	}{
		{"mixed-port: 7890\nproxies:\n  - name: n", FormatYAML},
		{"proxy-providers:\n  p:\n    url: https://example.invalid/list", FormatYAML},
		{"vless://uuid@example.invalid:443", FormatLink},
		{"  dmxlc3M6Ly8=\n", FormatBase64},
	}
	for _, tc := range cases {
		if got := DetectFormat([]byte(tc.input)); got != tc.format {
			t.Errorf("DetectFormat(%q) = %q, want %q", tc.input, got, tc.format)
		}
	}
}

func TestBase64ReaderVariantsAndThreePassLimit(t *testing.T) {
	plain := "vless://uuid@example.invalid:443\n"
	encoded := base64.RawURLEncoding.EncodeToString([]byte(plain))
	wrapped := strings.Join([]string{encoded[:4], "\n", encoded[4:]}, "")
	decoded, err := io.ReadAll(newBase64StreamReader(strings.NewReader(wrapped)))
	if err != nil {
		t.Fatalf("stream decode failed: %v", err)
	}
	if string(decoded) != plain {
		t.Fatalf("decoded content = %q", decoded)
	}
	percentDecoded, err := io.ReadAll(newBase64StreamReader(strings.NewReader("c3M6Ly8%3D")))
	if err != nil || string(percentDecoded) != "ss://" {
		t.Fatalf("percent-encoded padding was not decoded")
	}

	triple := base64.RawStdEncoding.EncodeToString([]byte(base64.RawStdEncoding.EncodeToString([]byte(base64.RawStdEncoding.EncodeToString([]byte(plain))))))
	var emitted []flight.Flight
	stats, err := Parse(strings.NewReader(triple), func(value flight.Flight) error {
		emitted = append(emitted, value)
		return nil
	})
	if err != nil {
		t.Fatalf("multi-pass parse failed: %v", err)
	}
	if stats.Base64Layers != 3 || len(emitted) != 1 || stats.Parsed != 1 || !emitted[0].FromBase64 {
		t.Fatalf("unexpected multi-pass stats: %+v", stats)
	}
}

func TestParseLinksOnlyDispatchesAndSkipsProtocols(t *testing.T) {
	var emitted []flight.Flight
	stats, err := Parse(strings.NewReader("ss://aes-128-gcm:secret@example.invalid:1#name\nvless://uuid@example.invalid:443\n"), func(value flight.Flight) error {
		emitted = append(emitted, value)
		return nil
	})
	if err != nil {
		t.Fatalf("parse links failed: %v", err)
	}
	if stats.Links != 1 || stats.Candidates != 2 || stats.Parsed != 2 || stats.Skipped != 0 || len(emitted) != 2 {
		t.Fatalf("unexpected link stats: %+v", stats)
	}
}

func TestParseYAMLBlocksAndProviders(t *testing.T) {
	content := `proxies:
  - name: first
    type: vless
    server: one.example
    port: 443
    uuid: first
  - {name: second, type: vmess, server: two.example, port: "8443", uuid: second}
proxy-providers:
  airport:
    url: https://provider.invalid/list
    interval: 3600
`
	var emitted []flight.Flight
	stats, err := Parse(strings.NewReader(content), func(value flight.Flight) error {
		emitted = append(emitted, value)
		return nil
	})
	if err != nil {
		t.Fatalf("parse YAML failed: %v", err)
	}
	if len(emitted) != 2 || stats.Parsed != 2 || len(stats.ProxyProviders) != 1 {
		t.Fatalf("unexpected YAML stats: %+v", stats)
	}
	if emitted[0].Name != "first" || emitted[1].Port != 8443 || stats.ProxyProviders[0].URL != "https://provider.invalid/list" {
		t.Fatalf("unexpected emitted values: %+v / %+v", emitted, stats.ProxyProviders)
	}
}

func TestParseYAMLKeepsUnknownTypesAndCountsMalformed(t *testing.T) {
	content := "proxies:\n  - name: unsupported\n    type: hysteria2\n    server: one.example\n    port: 443\n  - name: malformed\n    type: vless\n    port: nope\n"
	stats, err := Parse(strings.NewReader(content), func(flight.Flight) error { return nil })
	if err != nil {
		t.Fatalf("parse YAML failed: %v", err)
	}
	if stats.Candidates != 2 || stats.Parsed != 1 || stats.Skipped != 1 || stats.SkippedByReason["invalid YAML item"] != 1 {
		t.Fatalf("unexpected malformed stats: %+v", stats)
	}
}

func TestParseYAMLZeroIndentItems(t *testing.T) {
	content := "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 1\n  cipher: aes-128-gcm\n  password: p\n- {name: b, type: trojan, server: b.example, port: 2, password: q}\nrules:\n- MATCH,DIRECT\n"
	var names []string
	stats, err := Parse(strings.NewReader(content), func(value flight.Flight) error {
		names = append(names, value.Name)
		return nil
	})
	if err != nil {
		t.Fatalf("parse YAML failed: %v", err)
	}
	if strings.Join(names, ",") != "a,b" || stats.Candidates != 2 {
		t.Fatalf("zero-indent items: names=%v stats=%+v", names, stats)
	}
}

func TestLargeBase64DetectedAtAnyCut(t *testing.T) {
	line := "vless://uuid@example.invalid:443?security=reality#n\n"
	encoded := base64.StdEncoding.EncodeToString([]byte(strings.Repeat(line, 2000)))
	for cut := peekSize - 3; cut <= peekSize; cut++ {
		if got := DetectFormat([]byte(encoded[:cut])); got != FormatBase64 {
			t.Fatalf("cut %d detected as %q", cut, got)
		}
	}
	stats, err := Parse(strings.NewReader(encoded), func(flight.Flight) error { return nil })
	if err != nil || stats.Parsed != 2000 || stats.Skipped != 0 {
		t.Fatalf("large base64 parse: err=%v stats=%+v", err, stats)
	}
}
