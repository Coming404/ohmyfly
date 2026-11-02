package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
	"testing"
)

func TestXHTTPIntegerTextTypes(t *testing.T) {
	tests := []struct {
		name  string
		value any
		want  string
		valid bool
	}{
		{"float64", float64(1000000), "1000000", true},
		{"int", int(1), "1", true},
		{"int8", int8(-1), "-1", true},
		{"int16", int16(2), "2", true},
		{"int32", int32(3), "3", true},
		{"int64", int64(4), "4", true},
		{"uint", uint(5), "5", true},
		{"uint8", uint8(6), "6", true},
		{"uint16", uint16(7), "7", true},
		{"uint32", uint32(8), "8", true},
		{"uint64", uint64(9), "9", true},
		{"uintptr", uintptr(10), "10", true},
		{"json.Number exponent", json.Number("1e6"), "1000000", true},
		{"string range", "1-2", "1-2", true},
		{"fractional float64", float64(1.5), "", false},
		{"unsafe float64", float64(9007199254740992), "", false},
		{"bool", true, "", false},
		{"nil", nil, "", false},
		{"map", map[string]any{}, "", false},
		{"slice", []any{}, "", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := xhttpIntegerText(tt.value)
			if got != tt.want || ok != tt.valid {
				t.Fatalf("xhttpIntegerText(%#v) = (%q, %t), want (%q, %t)", tt.value, got, ok, tt.want, tt.valid)
			}
		})
	}
}

func TestXHTTPExtraJSONIntegerScalars(t *testing.T) {
	tests := []struct {
		name  string
		extra string
		field string
		want  any
	}{
		{"sc max one million", `{"scMaxEachPostBytes":1000000}`, "sc-max-each-post-bytes", 1000000},
		{"sc max two million", `{"scMaxEachPostBytes":2000000}`, "sc-max-each-post-bytes", 2000000},
		{"x padding numeric", `{"xPaddingBytes":1000000}`, "x-padding-bytes", "1000000"},
		{"xmux keep alive numeric", `{"xmux":{"hKeepAlivePeriod":1000000}}`, "reuse-settings", map[string]any{"h-keep-alive-period": 1000000}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			extra, err := parseXHTTPJSONObject(tt.extra, "extra")
			if err != nil {
				t.Fatal(err)
			}
			got, err := mapXHTTPExtra(extra, false)
			if err != nil {
				t.Fatal(err)
			}
			if wantMap, ok := tt.want.(map[string]any); ok {
				if !equalXHTTPMap(got[tt.field], wantMap) {
					t.Fatalf("%s = %#v, want %#v", tt.field, got[tt.field], tt.want)
				}
			} else if got[tt.field] != tt.want {
				t.Fatalf("%s = %#v, want %#v", tt.field, got[tt.field], tt.want)
			}
		})
	}
}

func TestXHTTPReuseCanonicalJSONIntegerPeriod(t *testing.T) {
	reuse, err := parseXHTTPJSONObject(`{"h-keep-alive-period":1000000}`, "reuse-settings")
	if err != nil {
		t.Fatal(err)
	}
	got, err := normalizeXHTTPReuseCanonical(reuse)
	if err != nil {
		t.Fatal(err)
	}
	if got["h-keep-alive-period"] != 1000000 {
		t.Fatalf("h-keep-alive-period = %#v, want 1000000", got["h-keep-alive-period"])
	}
}

func TestXHTTPExtraRejectsNonIntegerScalars(t *testing.T) {
	for _, tt := range []struct {
		name  string
		extra string
	}{
		{"fractional range value", `{"xPaddingBytes":1.5}`},
		{"boolean range value", `{"xPaddingBytes":true}`},
		{"fractional keep alive", `{"xmux":{"hKeepAlivePeriod":1.5}}`},
		{"boolean keep alive", `{"xmux":{"hKeepAlivePeriod":true}}`},
	} {
		t.Run(tt.name, func(t *testing.T) {
			extra, err := parseXHTTPJSONObject(tt.extra, "extra")
			if err != nil {
				t.Fatal(err)
			}
			if _, err := mapXHTTPExtra(extra, false); err == nil {
				t.Fatalf("mapXHTTPExtra(%s) succeeded", tt.extra)
			}
		})
	}
}

func TestXHTTPDownloadSettingsJSONPortAndDefaults(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	extra := url.QueryEscape(`{}`)
	download := url.QueryEscape(`{"address":"download.example.com","port":443,"xhttpSettings":{"extra":{}}}`)
	input := fmt.Sprintf("vless://%s@main.example.com:443?type=xhttp&extra=%s&downloadSettings=%s", uuid, extra, download)
	node, err := parseVLESSLink(input)
	if err != nil {
		t.Fatal(err)
	}
	options := node["xhttp-opts"].(map[string]any)
	settings := options["download-settings"].(map[string]any)
	if settings["port"] != 443 {
		t.Fatalf("download port = %#v, want 443", settings["port"])
	}
	wantReuse := map[string]any{
		"max-connections": "3", "h-max-request-times": "600-900", "h-max-reusable-secs": "1800-3000",
	}
	if got := settings["reuse-settings"]; !equalXHTTPMap(got, wantReuse) {
		t.Fatalf("download reuse-settings = %#v, want %#v", got, wantReuse)
	}
	if got := options["reuse-settings"]; !equalXHTTPMap(got, wantReuse) {
		t.Fatalf("main reuse-settings = %#v, want %#v", got, wantReuse)
	}
}

func TestXHTTPDownloadSettingsZeroKeepAliveUsesDefaults(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	extra := url.QueryEscape(`{"xmux":{"hKeepAlivePeriod":0}}`)
	download := url.QueryEscape(`{"address":"download.example.com","port":443,"xhttpSettings":{"extra":{"xmux":{"hKeepAlivePeriod":0}}}}`)
	input := fmt.Sprintf("vless://%s@main.example.com:443?type=xhttp&extra=%s&downloadSettings=%s", uuid, extra, download)
	node, err := parseVLESSLink(input)
	if err != nil {
		t.Fatal(err)
	}
	options := node["xhttp-opts"].(map[string]any)
	wantReuse := map[string]any{
		"h-keep-alive-period": 0, "max-connections": "3",
		"h-max-request-times": "600-900", "h-max-reusable-secs": "1800-3000",
	}
	if got := options["reuse-settings"]; !equalXHTTPMap(got, wantReuse) {
		t.Fatalf("main reuse-settings = %#v, want %#v", got, wantReuse)
	}
	settings := options["download-settings"].(map[string]any)
	if got := settings["reuse-settings"]; !equalXHTTPMap(got, wantReuse) {
		t.Fatalf("download reuse-settings = %#v, want %#v", got, wantReuse)
	}
}

func TestXHTTPDownloadSettingsRealityShortIDJSONNumber(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	const publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
	extra := url.QueryEscape(`{}`)
	download := url.QueryEscape(fmt.Sprintf(`{"address":"download.example.com","port":443,"security":"reality","realitySettings":{"password":%q,"shortId":1000000},"xhttpSettings":{"extra":{}}}`, publicKey))
	input := fmt.Sprintf("vless://%s@main.example.com:443?type=xhttp&extra=%s&downloadSettings=%s", uuid, extra, download)
	node, err := parseVLESSLink(input)
	if err != nil {
		t.Fatal(err)
	}
	options := node["xhttp-opts"].(map[string]any)
	settings := options["download-settings"].(map[string]any)
	reality := settings["reality-opts"].(map[string]any)
	if reality["short-id"] != "01000000" {
		t.Fatalf("download short-id = %#v, want 01000000", reality["short-id"])
	}
}

func equalXHTTPMap(got any, want map[string]any) bool {
	actual, ok := got.(map[string]any)
	if !ok || len(actual) != len(want) {
		return false
	}
	for key, value := range want {
		if actual[key] != value {
			return false
		}
	}
	return true
}
