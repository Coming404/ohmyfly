// Ported from SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import "testing"

func TestSubBoostSSRLinks(t *testing.T) {
	const secret = "secret"
	tests := []struct {
		name  string
		input string
		want  map[string]any
		err   bool
	}{
		// SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts:11.
		{
			name:  "decoded params and IPv6",
			input: "ssr://" + subBoostBase64URL("[2001:db8::1]:8388:auth_sha1_v4:aes-256-cfb:tls1.2_ticket_auth:"+subBoostBase64URL(secret)+"/?remarks="+subBoostBase64URL("SSR Node")+"&protoparam="+subBoostBase64URL("proto")+"&obfsparam="+subBoostBase64URL("plain-host")),
			want:  map[string]any{"name": "SSR Node", "server": "2001:db8::1", "port": 8388, "cipher": "aes-256-cfb", "password": secret, "protocol": "auth_sha1_v4", "protocol-param": "proto", "obfs": "tls1.2_ticket_auth", "obfs-param": "plain-host", "udp": true},
		},
		// SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts:31.
		{
			name:  "defaults and aliases",
			input: "ssr://" + subBoostBase64URL("ssr-default.example.com:8388::::"+subBoostBase64URL("plain pass")+"?remark="+subBoostBase64URL("Plain SSR")+"&protocol-param="+subBoostBase64URL("proto raw")+"&obfs-param="+subBoostBase64URL("obfs raw")),
			want:  map[string]any{"name": "Plain SSR", "cipher": "aes-256-cfb", "password": "plain pass", "protocol": "origin", "obfs": "plain", "protocol-param": "proto raw", "obfs-param": "obfs raw", "udp": true},
		},
		// SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts:67.
		{
			name:  "literal plus and percent",
			input: "ssr://" + subBoostBase64URL("literal.example.com:8388:origin:aes-256-cfb:plain:"+subBoostBase64URL("secret")+"/?remarks="+subBoostBase64URL("A+B%20C")+"&protoparam="+subBoostBase64URL("A+B%20C")+"&obfsparam="+subBoostBase64URL("A+B%20C")),
			want:  map[string]any{"name": "A+B%20C", "protocol-param": "A+B%20C", "obfs-param": "A+B%20C"},
		},
		// SubBoost packages/core/src/parser/protocols/ssr-snell.test.ts:91.
		{name: "invalid payload", input: "ssr://" + subBoostBase64URL("bad"), err: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseSSRLink(tt.input)
			if tt.err {
				if err == nil {
					t.Fatal("parseSSRLink succeeded")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, got, tt.want)
		})
	}
}
