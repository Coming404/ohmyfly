// Ported from SubBoost packages/core/src/parser/protocols/vmess-utils.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"net/url"
	"testing"
)

func TestSubBoostVMessUtils(t *testing.T) {
	// SubBoost packages/core/src/parser/protocols/vmess-utils.test.ts:17.
	t.Run("primitive values and headers", func(t *testing.T) {
		if dingtalkUserAgent == "" || !hasDingTalkHost([]string{"api.dingtalk.com"}) || hasDingTalkHost([]string{"example.com"}) {
			t.Fatal("DingTalk host detection mismatch")
		}
		for _, tt := range []struct {
			name string
			in   any
			want string
		}{
			{"post", "post", "POST"},
			{"empty", "   ", "GET"},
			{"bad", "bad method", "GET"},
			{"custom", "m-search", "M-SEARCH"},
		} {
			t.Run(tt.name, func(t *testing.T) {
				if got := normalizeHTTPMethod(tt.in); got != tt.want {
					t.Fatalf("normalizeHTTPMethod(%#v)=%q, want %q", tt.in, got, tt.want)
				}
			})
		}
		if normalizeHeaderKey(" user-agent ") != "User-Agent" || normalizeHeaderKey("connection") != "Connection" {
			t.Fatal("header key normalization mismatch")
		}
		got := parseHeaderRecord(map[string]any{"host": "cdn.example.com", "empty": "", "n": float64(1), "b": false, "list": []any{"a", "", "b"}, "bad": map[string]any{}})
		assertValueSubset(t, "headers", got, map[string]any{"Host": []string{"cdn.example.com"}, "n": []string{"1"}, "b": []string{"false"}, "list": []string{"a", "b"}})
		if parseHeaderRecord(map[string]any{"emptyList": []any{"", 1}, "nested": map[string]any{}}) != nil || parseHeaderRecord(nil) != nil {
			t.Fatal("empty header records should be omitted")
		}
		if got := splitList("a, b,,c"); len(got) != 3 || got[0] != "a" || got[2] != "c" || splitList("") != nil {
			t.Fatalf("splitList=%#v", got)
		}
		if pickString(" x ") != "x" || pickString(1) != "" {
			t.Fatal("pickString mismatch")
		}
		for _, tt := range []struct {
			input any
			want  bool
			ok    bool
		}{
			{"yes", true, true}, {"off", false, true}, {true, true, true}, {float64(0), false, true}, {float64(2), false, false}, {map[string]any{}, false, false}, {" ", false, false}, {"maybe", false, false},
		} {
			got, ok := parseBooleanish(tt.input)
			if got != tt.want || ok != tt.ok {
				t.Fatalf("parseBooleanish(%#v)=(%v,%v), want (%v,%v)", tt.input, got, ok, tt.want, tt.ok)
			}
		}
	})

	// SubBoost packages/core/src/parser/protocols/vmess-utils.test.ts:55.
	t.Run("style detection and query helpers", func(t *testing.T) {
		shadowrocket := subBoostBase64("auto:11111111-1111-1111-1111-111111111111@sr.example.com:443")
		params, err := url.ParseQuery("a=&b=value&c=next")
		if err != nil {
			t.Fatal(err)
		}
		cases := []struct {
			name string
			got  bool
			want bool
		}{
			{"uri", looksLikeURIStyleVMess("uuid@example.com:443"), true},
			{"not uri", looksLikeURIStyleVMess("plain"), false},
			{"standard", looksLikeStandardVMessStyle("ws+tls:uuid-0@example.com:443"), true},
			{"bad standard", looksLikeStandardVMessStyle("ws+tls:uuid-a@example.com:443"), false},
			{"shadowrocket", looksLikeShadowrocketStyleVMess(shadowrocket + "?obfs=websocket"), true},
			{"shadow at", looksLikeShadowrocketStyleVMess(shadowrocket + "@bad?obfs=websocket"), false},
			{"shadow empty", looksLikeShadowrocketStyleVMess("?obfs=websocket"), false},
			{"shadow bad base64", looksLikeShadowrocketStyleVMess("not-base64?obfs=websocket"), false},
		}
		for _, tt := range cases {
			if tt.got != tt.want {
				t.Errorf("%s=%v, want %v", tt.name, tt.got, tt.want)
			}
		}
		if stripOuterQuotes(`"quoted"`) != "quoted" || pickQueryParam(params, "a", "b", "c") != "value" || pickQueryParam(params, "missing", "a") != "" {
			t.Fatal("quote/query helper mismatch")
		}
		if parseObfsHeaderHost("Host: cdn.example.com, Path: /ws") != "cdn.example.com" || parseObfsHeaderHost("cdn.example.com") != "cdn.example.com" || parseObfsHeaderHost("") != "" {
			t.Fatal("obfs header host mismatch")
		}
	})
}
