// Ported from SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts @4015347 (AGPL-3.0-only).
// Regression cases ported from test/regression/core/parser-{edge-cases,matrix,link-edge-cases,protocol-edge-cases}.test.ts @4015347.
package towerparse

import "testing"

func TestSubBoostSSRLinks(t *testing.T) {
	b64 := subBoostBase64URL
	ssr := func(body string) string { return "ssr://" + b64(body) }
	testSimpleProxyLinks(t, parseSSRLink, []simpleProxyTestCase{
		// ssr-snell.test.ts:11.
		{name: "decoded params and IPv6", input: ssr("[2001:db8::1]:8388:auth_sha1_v4:aes-256-cfb:tls1.2_ticket_auth:" + b64("secret") + "/?remarks=" + b64("SSR Node") + "&protoparam=" + b64("proto") + "&obfsparam=" + b64("plain-host")), want: map[string]any{"name": "SSR Node", "type": "ssr", "server": "2001:db8::1", "port": 8388, "cipher": "aes-256-cfb", "password": "secret", "protocol": "auth_sha1_v4", "protocol-param": "proto", "obfs": "tls1.2_ticket_auth", "obfs-param": "plain-host", "udp": true}},
		// ssr-snell.test.ts:31.
		{name: "defaults and aliases", input: ssr("ssr-default.example.com:8388::::" + b64("plain pass") + "?remark=" + b64("Plain SSR") + "&protocol-param=" + b64("proto raw") + "&obfs-param=" + b64("obfs raw") + "&flag"), want: map[string]any{"name": "Plain SSR", "type": "ssr", "server": "ssr-default.example.com", "port": 8388, "cipher": "aes-256-cfb", "password": "plain pass", "protocol": "origin", "obfs": "plain", "protocol-param": "proto raw", "obfs-param": "obfs raw", "udp": true}},
		// ssr-snell.test.ts:49.
		{name: "no query defaults", input: ssr("no-query.example.com:8388::::" + b64("secret")), want: map[string]any{"name": "SSR-no-query.example.com:8388", "cipher": "aes-256-cfb", "protocol": "origin", "obfs": "plain"}},
		// ssr-snell.test.ts:55.
		{name: "empty query", input: ssr("empty-query.example.com:8388::::" + b64("secret") + "?"), want: map[string]any{"name": "SSR-empty-query.example.com:8388", "password": "secret"}},
		// ssr-snell.test.ts:59.
		{name: "empty pairs skipped", input: ssr("raw-param.example.com:8388:auth:aes-128-gcm:http:" + b64("plain secret") + "/?remarks=" + b64("Raw SSR") + "&&=skip&protoparam=" + b64("proto raw") + "&obfsparam=" + b64("obfs raw")), want: map[string]any{"name": "Raw SSR", "type": "ssr", "password": "plain secret", "protocol": "auth", "cipher": "aes-128-gcm", "obfs": "http", "protocol-param": "proto raw", "obfs-param": "obfs raw"}},
		// ssr-snell.test.ts:78.
		{name: "literal plus and percent", input: ssr("literal.example.com:8388:origin:aes-256-cfb:plain:" + b64("secret") + "/?remarks=" + b64("A+B%20C") + "&protoparam=" + b64("A+B%20C") + "&obfsparam=" + b64("A+B%20C")), want: map[string]any{"name": "A+B%20C", "protocol-param": "A+B%20C", "obfs-param": "A+B%20C"}},
		// ssr-snell.test.ts:92-95.
		{name: "empty link", input: "ssr://", message: "empty ssr link"},
		{name: "too few parts", input: ssr("bad"), message: "invalid ssr format"},
		{name: "missing server", input: ssr(":8388:origin:aes-256-cfb:plain:" + b64("secret")), message: "missing server"},
		{name: "port out of range", input: ssr("ssr.example.com:70000:origin:aes-256-cfb:plain:" + b64("secret")), message: "invalid port"},
	})
}

func TestSubBoostSSRRegressionLinks(t *testing.T) {
	b64 := subBoostBase64URL
	std := subBoostBase64
	// 对应回归测试里的 ssr(main, query) 辅助函数：外层用标准 Base64。
	ssr := func(main, query string) string {
		if query != "" {
			main += "/?" + query
		}
		return "ssr://" + std(main)
	}
	testSimpleProxyLinks(t, parseSSRLink, []simpleProxyTestCase{
		// parser-edge-cases.test.ts:308.
		{name: "remarks and flag", input: ssr("[2001:db8::1]:8388:origin:aes-256-cfb:plain:"+b64("pw"), "remarks="+b64("plain-name")+"&flag"), want: map[string]any{"name": "plain-name", "server": "2001:db8::1", "password": "pw"}},
		// parser-edge-cases.test.ts:313.
		{name: "ignored empty keys", input: ssr("[2001:db8::10]:8388::::"+b64("pw"), "flag&=ignored&&remarks="+b64("plain name")), want: map[string]any{"name": "plain name", "server": "2001:db8::10", "cipher": "aes-256-cfb", "protocol": "origin", "obfs": "plain"}},
		// parser-edge-cases.test.ts:320.
		{name: "non-base64 param", input: ssr("fallback.example.com:8388:::plain:"+b64("pw"), "protoparam=not-base64&obfsparam="), want: map[string]any{"cipher": "aes-256-cfb", "protocol": "origin"}, absent: []string{"obfs-param"}},
		// parser-edge-cases.test.ts:326-328.
		{name: "bad format", input: "ssr://" + std("bad:format"), message: "invalid ssr format"},
		{name: "regression missing server", input: ssr(":8388:origin:aes-256-cfb:plain:"+b64("pw"), ""), message: "missing server"},
		{name: "regression port out of range", input: ssr("bad.example.com:70000:origin:aes-256-cfb:plain:"+b64("pw"), ""), message: "invalid port"},
		// parser-matrix.test.ts:513-516.
		{name: "not base64", input: "ssr://bad", message: "invalid ssr format"},
		{name: "too few", input: "ssr://" + std("too:few"), message: "invalid ssr format"},
		{name: "port zero", input: "ssr://" + std("ssr.example.com:0:origin:aes-256-cfb:plain:"+std("pw")), message: "invalid port"},
		// parser-matrix.test.ts:517.
		{name: "standard base64 params", input: "ssr://" + std("[2001:db8::2]:8388:auth_aes128_md5:aes-256-cfb:tls1.2_ticket_auth:"+std("pw")+"/?remarks="+std("Plain Remark")+"&flag&protoparam="+std("proto")+"&obfsparam="+std("front.example.com")), want: map[string]any{"name": "Plain Remark", "server": "2001:db8::2", "protocol-param": "proto", "obfs-param": "front.example.com"}},
		// parser-link-edge-cases.test.ts:199.
		{name: "empty flag", input: ssr("[2001:db8::1]:443::::"+std("pw"), "emptyFlag&remarks="+std("PlainName")), want: map[string]any{"name": "PlainName", "server": "2001:db8::1", "cipher": "aes-256-cfb", "protocol": "origin", "obfs": "plain"}},
		// parser-protocol-edge-cases.test.ts:93.
		{name: "flag only query", input: "ssr://" + b64("flag.example.com:8388::::"+b64("secret")+"/?flag"), want: map[string]any{"server": "flag.example.com", "password": "secret", "protocol": "origin", "cipher": "aes-256-cfb", "obfs": "plain"}},
		{name: "plain without query", input: "ssr://" + b64("plain.example.com:8388::::"+b64("secret")), want: map[string]any{"server": "plain.example.com"}},
		// 上游 safeDecodeSsrBase64 对非 Base64 值做宽松 URL 解码，非法转义保留原文，节点不丢弃。
		{name: "literal percent remarks", input: ssr("h.example.com:8388:origin:aes-256-cfb:plain:"+b64("pw"), "remarks=100%"), want: map[string]any{"name": "100%"}},
		{name: "bad percent password", input: ssr("h.example.com:8388:origin:aes-256-cfb:plain:p%w", ""), want: map[string]any{"password": "p%w"}},
		{name: "percent encoded remarks", input: ssr("h.example.com:8388:origin:aes-256-cfb:plain:"+b64("pw"), "remarks=%E4%B8%AD+%E6%96%87"), want: map[string]any{"name": "中 文"}},
		// 上游端口用 Number.parseInt，数字后的多余字符被忽略。
		{name: "port trailing junk", input: ssr("h.example.com:8388abc:origin:aes-256-cfb:plain:"+b64("pw"), ""), want: map[string]any{"port": 8388}},
	})
}
