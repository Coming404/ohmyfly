package towerparse

import (
	"fmt"
	"net/url"
	"testing"
)

func TestXHTTPParityEmptySourceRanges(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:50-56,88-91: source empties become zero before fallbacks.
	for _, tt := range []struct {
		source, field string
		want          any
	}{
		{"sessionIDLength", "session-length", "0"},
		{"uplinkChunkSize", "uplink-chunk-size", "0"},
		{"scMaxEachPostBytes", "sc-max-each-post-bytes", 1000000},
		{"scMinPostsIntervalMs", "sc-min-posts-interval-ms", "30"},
		{"xPaddingBytes", "x-padding-bytes", "100-1000"},
	} {
		for _, empty := range []string{"", " \t "} {
			t.Run(tt.source+fmt.Sprintf("/%q", empty), func(t *testing.T) {
				source, err := parseXHTTPJSONObject(fmt.Sprintf(`{%q:%q}`, tt.source, empty), "extra")
				if err != nil {
					t.Fatal(err)
				}
				got, err := mapXHTTPExtra(source, false)
				if err != nil {
					t.Fatal(err)
				}
				if got[tt.field] != tt.want {
					t.Fatalf("%s = %#v, want %#v", tt.field, got[tt.field], tt.want)
				}
			})
		}
	}
}

func TestXHTTPParityRangeOrder(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:50-62,88,196-202 sorts only source ranges.
	// SubBoost packages/core/src/mihomo/xhttp.ts:5-15 rejects descending canonical/flat ranges and retains empty ranges.
	for _, tt := range []struct {
		source, field string
		reuse         bool
	}{
		{"xPaddingBytes", "x-padding-bytes", false},
		{"sessionIDLength", "session-length", false},
		{"uplinkChunkSize", "uplink-chunk-size", false},
		{"scMaxEachPostBytes", "sc-max-each-post-bytes", false},
		{"scMinPostsIntervalMs", "sc-min-posts-interval-ms", false},
		{"maxConcurrency", "max-concurrency", true},
		{"maxConnections", "max-connections", true},
		{"cMaxReuseTimes", "c-max-reuse-times", true},
		{"hMaxRequestTimes", "h-max-request-times", true},
		{"hMaxReusableSecs", "h-max-reusable-secs", true},
	} {
		t.Run(tt.field, func(t *testing.T) {
			source := map[string]any{tt.source: "+4 - +2"}
			if tt.reuse {
				source = map[string]any{"xmux": source}
			}
			got, err := mapXHTTPExtra(source, false)
			if err != nil {
				t.Fatal(err)
			}
			if tt.reuse {
				got = got["reuse-settings"].(map[string]any)
			}
			if got[tt.field] != "2-4" {
				t.Fatalf("source %s = %#v, want 2-4", tt.field, got[tt.field])
			}
			for _, raw := range []string{"4-2", ""} {
				canonical := map[string]any{tt.field: raw}
				if tt.reuse {
					canonical = map[string]any{"reuse-settings": canonical}
				}
				normalized, err := normalizeXHTTPFields(canonical)
				if raw != "" {
					if err == nil {
						t.Error("descending canonical range accepted")
					}
				} else {
					if err != nil {
						t.Fatal(err)
					}
					if tt.reuse {
						normalized = normalized["reuse-settings"].(map[string]any)
					}
					if normalized[tt.field] != "" {
						t.Errorf("empty canonical range = %#v", normalized[tt.field])
					}
				}
			}
			if _, err := mergeXHTTPExtra(url.Values{tt.field: {"4-2"}}, map[string]any{}); err == nil {
				t.Error("descending flat alias accepted")
			}
		})
	}
}

func TestXHTTPParityRangeSyntax(t *testing.T) {
	// SubBoost packages/core/src/mihomo/xhttp.ts:9-13 and parser/protocols/vless-xhttp.ts:52-55 allow one adjacent plus only.
	for _, raw := range []string{"++2", "+ 2", "2-++4", "2-+ 4", "2147483648", "1e3"} {
		t.Run(raw, func(t *testing.T) {
			if _, err := normalizeXHTTPFields(map[string]any{"session-length": raw}); err == nil {
				t.Error("invalid canonical range accepted")
			}
			if _, err := mapXHTTPExtra(map[string]any{"sessionIDLength": raw}, false); err == nil {
				t.Error("invalid source range accepted")
			}
		})
	}
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:60-62 also applies sourceRange to xmux.
	got, err := mapXHTTPExtra(map[string]any{"xmux": map[string]any{"maxConcurrency": ""}}, false)
	if err != nil {
		t.Fatal(err)
	}
	if !equalXHTTPMap(got["reuse-settings"], map[string]any{"max-concurrency": "0"}) {
		t.Fatalf("empty source xmux = %#v", got["reuse-settings"])
	}
}

func TestXHTTPParityZeroEndpoints(t *testing.T) {
	// SubBoost packages/core/src/mihomo/xhttp.ts:50-54 rejects zero maxima only for sc-*, not x-padding-bytes.
	for _, field := range []string{"sc-max-each-post-bytes", "sc-min-posts-interval-ms"} {
		for _, raw := range []any{"0", "0-0", float64(0)} {
			t.Run(field+fmt.Sprintf("/%v", raw), func(t *testing.T) {
				if _, err := normalizeXHTTPFields(map[string]any{field: raw}); err == nil {
					t.Error("zero canonical sc range accepted")
				}
			})
		}
		if _, err := mergeXHTTPExtra(url.Values{field: {"0"}}, map[string]any{}); err == nil {
			t.Errorf("zero flat %s accepted", field)
		}
	}
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:89-92 falls back on scalar zero, then rejects remaining zero endpoints.
	for _, raw := range []string{"0", "0-0", "0-1", "1-0"} {
		t.Run("padding/"+raw, func(t *testing.T) {
			got, err := mapXHTTPExtra(map[string]any{"xPaddingBytes": raw}, false)
			if raw == "0" || raw == "0-0" {
				if err != nil || got["x-padding-bytes"] != "100-1000" {
					t.Fatalf("source padding = %#v, %v", got, err)
				}
			} else if err == nil {
				t.Error("source padding zero endpoint accepted")
			}
		})
	}
	for _, raw := range []string{"0", "0-0", "0-1"} {
		got, err := normalizeXHTTPFields(map[string]any{"x-padding-bytes": raw})
		want := raw
		if raw == "0-0" {
			want = "0"
		}
		if err != nil || got["x-padding-bytes"] != want {
			t.Fatalf("canonical padding %q = %#v, %v", raw, got, err)
		}
	}
}

func TestXHTTPParityPaddingMethod(t *testing.T) {
	// SubBoost packages/core/src/mihomo/xhttp.ts:56-66 allows only empty, repeat-x, or tokenish.
	for _, method := range []string{"", "repeat-x", "tokenish", "invalid", "TOKENISH", " repeat-x "} {
		t.Run(fmt.Sprintf("%q", method), func(t *testing.T) {
			valid := method == "" || method == "repeat-x" || method == "tokenish"
			got, err := normalizeXHTTPFields(map[string]any{"x-padding-method": method})
			if (err == nil) != valid {
				t.Fatalf("canonical method %q: %v, want valid=%t", method, err, valid)
			}
			if valid && got["x-padding-method"] != method {
				t.Errorf("method changed to %#v", got["x-padding-method"])
			}
			if _, err := mapXHTTPExtra(map[string]any{"xPaddingMethod": method}, false); (err == nil) != valid {
				t.Errorf("source method %q: %v, want valid=%t", method, err, valid)
			}
		})
	}
}

func TestXHTTPParityNullAndMissing(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:60,70-73: null/missing xmux both acquire source defaults.
	for _, extra := range []string{`{}`, `{"xmux":null}`} {
		t.Run(extra, func(t *testing.T) {
			node, err := parseVLESSLink("vless://11111111-1111-4111-8111-111111111111@main.example:443?type=xhttp&extra=" + url.QueryEscape(extra))
			if err != nil {
				t.Fatal(err)
			}
			opts := node["xhttp-opts"].(map[string]any)
			want := map[string]any{"max-connections": "3", "h-max-request-times": "600-900", "h-max-reusable-secs": "1800-3000"}
			if !equalXHTTPMap(opts["reuse-settings"], want) {
				t.Fatalf("reuse-settings = %#v, want %#v", opts["reuse-settings"], want)
			}
		})
	}
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:208-211,123,32-35: present null downloadSettings reaches object() and fails.
	if _, err := mergeXHTTPExtra(url.Values{"extra": {`{"downloadSettings":null}`}}, map[string]any{}); err == nil {
		t.Error("null extra.downloadSettings accepted")
	}
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:133-135,145 rejects these keys even when null.
	for _, field := range []string{"sockopt", "finalmask", "nested downloadSettings"} {
		t.Run(field, func(t *testing.T) {
			source := map[string]any{"address": "download.example", "port": float64(443)}
			if _, err := mapXHTTPDownload(source, map[string]any{}); err != nil {
				t.Fatal(err)
			}
			if field == "nested downloadSettings" {
				source["xhttpSettings"] = map[string]any{"downloadSettings": nil}
			} else {
				source[field] = nil
			}
			if _, err := mapXHTTPDownload(source, map[string]any{}); err == nil {
				t.Error("present null key accepted")
			}
		})
	}
}

func TestXHTTPParityWhitespaceDefaults(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:74-81 uses raw JS falsy, not trimmed emptiness; method is uppercased without trim.
	for _, tt := range []struct {
		extra, field, want string
	}{
		{`{"uplinkHTTPMethod":" get "}`, "uplink-http-method", " GET "},
		{`{"uplinkHTTPMethod":" "}`, "uplink-http-method", " "},
		{`{"uplinkHTTPMethod":""}`, "uplink-http-method", "POST"},
		{`{"sessionIDTable":" ","sessionIDLength":16}`, "session-table", " "},
		{`{"sessionIDTable":" uuid ","sessionIDLength":16}`, "session-table", " uuid "},
		{`{"uplinkDataKey":" "}`, "uplink-data-key", " "},
		{`{"xPaddingObfsMode":true,"xPaddingKey":" "}`, "x-padding-key", " "},
		{`{"xPaddingObfsMode":true,"xPaddingHeader":" "}`, "x-padding-header", " "},
	} {
		t.Run(tt.extra, func(t *testing.T) {
			node, err := parseVLESSLink("vless://11111111-1111-4111-8111-111111111111@main.example:443?type=xhttp&extra=" + url.QueryEscape(tt.extra))
			if err != nil {
				t.Fatal(err)
			}
			got := node["xhttp-opts"].(map[string]any)[tt.field]
			if got != tt.want {
				t.Fatalf("%s = %#v, want %q", tt.field, got, tt.want)
			}
		})
	}
}

func TestXHTTPParityWhitespaceFlatAliases(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:196-202 selects the first raw nonempty alias, even a whitespace string.
	for _, query := range []url.Values{
		{"extra": {`{}`}, "uplink-http-method": {" get "}},
		{"extra": {`{}`}, "uplink-http-method": {" "}, "uplinkHTTPMethod": {"GET"}},
		{"extra": {`{}`}, "uplink-data-key": {" "}},
		{"uplink-http-method": {" get "}},
	} {
		t.Run(query.Encode(), func(t *testing.T) {
			got, err := mergeXHTTPExtra(query, map[string]any{})
			if err != nil {
				t.Fatal(err)
			}
			field, want := "uplink-http-method", query.Get("uplink-http-method")
			if _, exists := query["uplink-data-key"]; exists {
				field, want = "uplink-data-key", " "
			} else if query.Has("extra") && want == " get " {
				want = " GET "
			}
			if got[field] != want {
				t.Fatalf("%s = %#v, want %q", field, got[field], want)
			}
		})
	}
}

func TestXHTTPParitySharedWhitespace(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:105-113,140-143 treats a whitespace session-key as a literal shared value.
	for _, key := range []string{"", " "} {
		t.Run(fmt.Sprintf("%q", key), func(t *testing.T) {
			main := map[string]any{"session-placement": "header", "session-key": " "}
			download := map[string]any{
				"address": "download.example", "port": float64(443),
				"xhttpSettings": map[string]any{"sessionIDPlacement": "header", "sessionIDKey": key},
			}
			_, err := mapXHTTPDownload(download, main)
			if (err == nil) != (key == " ") {
				t.Fatalf("shared session-key %q: %v, want accepted=%t", key, err, key == " ")
			}
		})
	}
}

func TestXHTTPParityDownloadNetworkAndPort(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:124-128 defaults only missing network, and string ports must match /^\d+$/.
	for _, tt := range []struct {
		name, fields string
		valid        bool
	}{
		{"missing network", `"port":443`, true},
		{"xhttp", `"network":"xhttp","port":443`, true},
		{"splithttp", `"network":"splithttp","port":443`, true},
		{"empty network", `"network":"","port":443`, false},
		{"spaced network", `"network":" xhttp ","port":443`, false},
		{"digit port", `"port":"00443"`, true},
		{"positive sign", `"port":"+443"`, false},
		{"spaced port", `"port":" 443 "`, false},
		{"fractional port", `"port":443.5`, false},
		{"exponent numeric port", `"port":4.43e2`, true},
		{"exponent string port", `"port":"4.43e2"`, false},
		{"unicode digit port", `"port":"４４３"`, false},
		{"zero port", `"port":0`, false},
		{"large port", `"port":65536`, false},
	} {
		t.Run(tt.name, func(t *testing.T) {
			source, err := parseXHTTPJSONObject(`{"address":"download.example",`+tt.fields+`}`, "downloadSettings")
			if err != nil {
				t.Fatal(err)
			}
			got, err := mapXHTTPDownload(source, map[string]any{})
			if (err == nil) != tt.valid {
				t.Fatalf("mapXHTTPDownload: %v, want accepted=%t", err, tt.valid)
			}
			if tt.valid && got["port"] != 443 {
				t.Fatalf("port = %#v, want integer 443", got["port"])
			}
		})
	}
}

func TestXHTTPParityDownloadStringTypes(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:116-119,124-131,151,160-164 rejects non-string values, including null.
	for _, field := range []string{"network", "address", "security", "path", "host", "serverName", "fingerprint"} {
		for _, raw := range []string{`null`, `123`, `true`, `[]`, `{}`} {
			t.Run(field+"/"+raw, func(t *testing.T) {
				source, err := parseXHTTPJSONObject(`{"address":"download.example","port":443,"xhttpSettings":{},"tlsSettings":{}}`, "downloadSettings")
				if err != nil {
					t.Fatal(err)
				}
				value, err := parseXHTTPJSONObject(`{"value":`+raw+`}`, "test value")
				if err != nil {
					t.Fatal(err)
				}
				target := source
				switch field {
				case "path", "host":
					target = source["xhttpSettings"].(map[string]any)
				case "serverName", "fingerprint":
					target = source["tlsSettings"].(map[string]any)
				}
				target[field] = value["value"]
				if _, err := mapXHTTPDownload(source, map[string]any{}); err == nil {
					t.Error("non-string download field accepted")
				}
			})
		}
	}
}

func TestXHTTPParityDownloadWhitespaceAndFingerprint(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vless-xhttp.ts:126,151,160-164 trims only address; string defaults apply only to missing keys.
	source, err := parseXHTTPJSONObject(`{"address":" download.example ","port":443,"xhttpSettings":{"path":" ","host":" host "},"tlsSettings":{"serverName":" sni ","fingerprint":" fp "}}`, "downloadSettings")
	if err != nil {
		t.Fatal(err)
	}
	got, err := mapXHTTPDownload(source, map[string]any{})
	if err != nil {
		t.Fatal(err)
	}
	for field, want := range map[string]any{"server": "download.example", "path": " ", "host": " host ", "servername": " sni ", "client-fingerprint": " fp "} {
		if got[field] != want {
			t.Errorf("%s = %#v, want %#v", field, got[field], want)
		}
	}
	for _, tt := range []struct {
		name, field, want string
	}{
		{"missing", "", "chrome"},
		{"empty", `,"fingerprint":""`, ""},
		{"spaces", `,"fingerprint":" "`, " "},
	} {
		t.Run(tt.name, func(t *testing.T) {
			source, err := parseXHTTPJSONObject(`{"address":"download.example","port":443,"security":"reality","realitySettings":{"publicKey":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"`+tt.field+`}}`, "downloadSettings")
			if err != nil {
				t.Fatal(err)
			}
			got, err := mapXHTTPDownload(source, map[string]any{})
			if err != nil {
				t.Fatal(err)
			}
			if got["client-fingerprint"] != tt.want {
				t.Fatalf("fingerprint = %#v, want %q", got["client-fingerprint"], tt.want)
			}
		})
	}
}
