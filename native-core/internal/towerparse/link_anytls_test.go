// Ported from SubBoost packages/core/src/parser/protocols/anytls.ts @4015347 (AGPL-3.0-only).
// Tests ported from protocols/more-protocols.test.ts and ech-protocols.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"net/url"
	"strings"
	"testing"
)

func TestAnyTLSLinks(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			name: "upstream encoded userinfo",
			input: "anytls://" + subBoostBase64URL("prefix:secret@anytls.example.com:443") +
				"?sni=sni.example.com&alpn=h2,http/1.1&fp=chrome&udp=0&ech=ZWNo" +
				"&idle-session-check-interval=10&idle-session-timeout=20&min-idle-session=2" +
				"&padding-scheme=pad&pcs=pcs&pqv=pqv#AnyTLS",
			want: map[string]any{
				"name": "AnyTLS", "type": "anytls", "server": "anytls.example.com", "port": 443,
				"password": "secret", "udp": false, "sni": "sni.example.com",
				"alpn": []string{"h2", "http/1.1"}, "client-fingerprint": "chrome",
				"ech-opts":                    map[string]any{"enable": true, "config": "ZWNo"},
				"idle-session-check-interval": 10, "idle-session-timeout": 20, "min-idle-session": 2,
				"padding-scheme": "pad", "pqv": "pqv",
			},
			absent: []string{"pcs", "fingerprint"},
		},
		{
			name: "upstream direct credentials",
			input: "anytls://user:pass@direct.example.com?security=none&type=none&headerType=none" +
				"&peer=peer.example.com&allow_insecure=true&udp=maybe&fingerprint=firefox&ech=&remarks=Direct",
			want: map[string]any{
				"name": "Direct", "port": 443, "password": "user:pass", "udp": true,
				"sni": "peer.example.com", "skip-cert-verify": true, "client-fingerprint": "firefox",
				"ech-opts": map[string]any{"enable": true},
			},
		},
		{
			name: "upstream query aliases",
			input: "anytls://query.example.com:8443?auth_str=secret&servername=sni.example.com" +
				"&clientFingerprint=safari&alpn=h2,,http/1.1&idleSessionCheckInterval=bad" +
				"&idleSessionTimeout=30&minIdleSession=4&paddingScheme=pad",
			want: map[string]any{
				"name": "query.example.com:8443", "password": "secret", "sni": "sni.example.com",
				"alpn": []string{"h2", "http/1.1"}, "client-fingerprint": "safari",
				"idle-session-timeout": 30, "min-idle-session": 4, "padding-scheme": "pad",
			},
			absent: []string{"idle-session-check-interval", "ech-opts"},
		},
		{
			name: "upstream empty number falls through",
			input: "anytls://host-token.example.com?auth=secret&udp=1&alpn=,," +
				"&idle-session-check-interval=&idleSessionCheckInterval=15",
			want: map[string]any{
				"name": "host-token.example.com:443", "password": "secret", "udp": true,
				"sni": "host-token.example.com", "idle-session-check-interval": 15,
			},
			absent: []string{"alpn"},
		},
		{
			name: "upstream TCP safe contract",
			input: "anytls://secret@anytls.example.com:443?alpn=h2,http%2F1.1&allowInsecure=true&fp=chrome" +
				"&idle-session-check-interval=60&idle-session-timeout=120&min-idle-session=2" +
				"&padding-scheme=100-200&ech=Y29uZmln#AnyTLS",
			want: map[string]any{
				"name": "AnyTLS", "password": "secret", "udp": true, "skip-cert-verify": true,
				"client-fingerprint": "chrome", "alpn": []string{"h2", "http/1.1"},
				"idle-session-check-interval": 60, "idle-session-timeout": 120, "min-idle-session": 2,
				"padding-scheme": "100-200", "ech-opts": map[string]any{"enable": true, "config": "Y29uZmln"},
			},
		},
		{
			name:  "IPv6 signed port and one credential decode",
			input: "anytls://u:p%3A%2B%2520@[2001:db8::1]:+12#%2520%2B+%E4%B8%AD",
			want: map[string]any{
				"server": "2001:db8::1", "port": 12, "password": "u:p:+%20", "name": "%20+ 中",
			},
		},
		{
			name:  "IPv6 default name and numeric port prefix",
			input: "anytls://secret@[2001:db8::1]:443abc",
			want:  map[string]any{"name": "[2001:db8::1]:443", "port": 443},
		},
		{
			name:  "last at and malformed percent are preserved",
			input: "anytls://u@p%zz@anytls.example.com#bad+%zz",
			want:  map[string]any{"password": "u@p%zz", "name": "bad+%zz"},
		},
		{
			name: "query decoding and raw truthy alias priority",
			input: "anytls://anytls.example.com?password=++p%zz++&auth=ignored&remarks=A%2520+B" +
				"&sni=+&peer=ignored&fp=+&fingerprint=ignored&padding-scheme=+&paddingScheme=ignored" +
				"&allowInsecure=TRUE&insecure=yes&udp=no&pqv=+",
			want:   map[string]any{"password": "p%zz", "name": "A  B", "sni": " ", "udp": true, "pqv": " "},
			absent: []string{"client-fingerprint", "padding-scheme", "skip-cert-verify"},
		},
		{
			name: "integer aliases use parseInt prefixes",
			input: "anytls://secret@anytls.example.com?idle-session-check-interval=bad&idleSessionCheckInterval=%2B12ms" +
				"&idle-session-timeout=-3.5&min-idle-session=0x10&padding_scheme=+pad+&udp=FALSE",
			want: map[string]any{
				"idle-session-check-interval": 12, "idle-session-timeout": -3, "min-idle-session": 0,
				"padding-scheme": "pad", "udp": false,
			},
		},
		{
			name:  "encoded credentials strip through final colon",
			input: "anytls://" + subBoostBase64("prefix:more:secret@encoded.example.com:443") + "?remark=Encoded+Name",
			want:  map[string]any{"password": "secret", "name": "Encoded Name"},
		},
		{
			name:  "encoded userinfo ignores content after padding",
			input: "anytls://" + subBoostBase64URL("prefix:secret@encoded.example.com:443") + "=ignored?udp=0",
			want:  map[string]any{"password": "secret", "server": "encoded.example.com", "udp": false},
		},
		{
			name:  "encoded userinfo ignores incomplete final sextet",
			input: "anytls://" + subBoostBase64URL("u:p@h:443") + "A?udp=0",
			want:  map[string]any{"password": "p", "server": "h", "port": 443},
		},
		{
			name:  "empty username and nonempty password",
			input: "anytls://:secret@anytls.example.com?password=ignored&network=TCP&header_type=none",
			want:  map[string]any{"password": ":secret", "name": "anytls.example.com:443"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseAnyTLSLink(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestAnyTLSInvalidLinks(t *testing.T) {
	cases := []struct{ name, input, message string }{
		{"wrong scheme", "http://bad", "无效的 AnyTLS 链接"},
		{"case sensitive scheme", "AnyTLS://secret@h", "无效的 AnyTLS 链接"},
		{"missing server", "anytls://secret@:443", "AnyTLS 配置缺少必要字段"},
		{"missing password", "anytls://anytls.example.com", "AnyTLS 配置缺少必要字段"},
		{"truthy whitespace password blocks alias", "anytls://h?password=+&auth=secret", "AnyTLS 配置缺少必要字段"},
		{"encoded authority needs query", "anytls://" + subBoostBase64URL("u:p@h:443"), "AnyTLS 配置缺少必要字段"},
		{"port too large", "anytls://secret@h:70000", "无效的端口号"},
		{"hex port prefix is zero", "anytls://secret@h:0x10", "无效的端口号"},
		{"zero port", "anytls://secret@h:0", "无效的端口号"},
		{"negative port", "anytls://secret@h:-12", "无效的端口号"},
		{"malformed port", "anytls://secret@h:%zz", "无效的端口号"},
		{"malformed IPv6", "anytls://secret@[::1", "invalid URL"},
		{"reality security", "anytls://secret@h?security=reality", "AnyTLS 不支持 security=reality"},
		{"unknown security", "anytls://secret@h?security=xtls", "AnyTLS 不支持 security=xtls"},
		{"websocket transport", "anytls://secret@h?type=ws", "AnyTLS 不支持 type=ws"},
		{"network alias", "anytls://secret@h?network=grpc", "AnyTLS 不支持 type=grpc"},
		{"transport alias", "anytls://secret@h?transport=h2", "AnyTLS 不支持 type=h2"},
		{"HTTP header", "anytls://secret@h?type=tcp&headerType=http", "AnyTLS 不支持 headerType=http"},
		{"header alias", "anytls://secret@h?header-type=http", "AnyTLS 不支持 headerType=http"},
	}
	for _, key := range []string{
		"pbk", "sid", "spx", "public-key", "public_key", "publicKey", "short-id", "short_id", "shortId",
		"serviceName", "service_name", "service-name", "authority", "host", "path", "seed", "mode",
	} {
		cases = append(cases, struct{ name, input, message string }{
			name: "unsupported parameter " + key, input: "anytls://secret@h?" + key + "=", message: "AnyTLS 不支持",
		})
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := parseAnyTLSLink(tc.input); err == nil || !strings.Contains(err.Error(), tc.message) {
				t.Fatalf("error = %v, want %q", err, tc.message)
			}
		})
	}
}

func TestAnyTLSCertificateFingerprint(t *testing.T) {
	hex := strings.Repeat("ab", 32)
	other := strings.Repeat("cd", 32)
	for _, tc := range []struct{ name, query, want string }{
		{"pcs cleaned", "pcs=" + strings.Repeat("AB:", 31) + "AB", hex},
		{"hpkp cleaned", "hpkp=" + strings.Repeat("AB-", 31) + "AB", hex},
		{"pcs wins", "pcs=" + hex + "&hpkp=" + other, hex},
		{"invalid pcs falls through", "pcs=bad&hpkp=" + hex, hex},
		{"first valid list item", "pcs=bad," + hex + "," + other, hex},
		{"invalid fingerprints absent", "pcs=bad&hpkp=also-bad", ""},
		{"empty pcs falls through", "pcs=&hpkp=" + hex, hex},
	} {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseAnyTLSLink("anytls://secret@h?fp=chrome&fingerprint=firefox&" + tc.query)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, map[string]any{"client-fingerprint": "chrome"})
			assertMapAbsent(t, node, "pcs", "hpkp")
			if tc.want == "" {
				assertMapAbsent(t, node, "fingerprint")
			} else {
				assertMapSubset(t, node, map[string]any{"fingerprint": tc.want})
			}
		})
	}
}

func TestAnyTLSECH(t *testing.T) {
	for _, tc := range []struct {
		name, value string
		want        map[string]any
	}{
		{"domain", "cloudflare-ech.com", map[string]any{"enable": true, "query-server-name": "cloudflare-ech.com"}},
		{
			"compound", "cloudflare-ech.com+https://203.0.113.53/dns-query",
			map[string]any{"enable": true, "query-server-name": "cloudflare-ech.com"},
		},
		{"base64", "+w==", map[string]any{"enable": true, "config": "+w=="}},
		{"empty", "", map[string]any{"enable": true}},
		{"invalid config", "not base64!", map[string]any{"enable": true}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseAnyTLSLink("anytls://secret@anytls-ech.example.com?ech=" + url.QueryEscape(tc.value))
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, map[string]any{"ech-opts": tc.want})
		})
	}
}
