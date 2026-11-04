// Ported from SubBoost packages/core/src/parser/protocols/more-protocols.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func TestTUICLinks(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			name:  "upstream query token wins",
			input: "tuic://token-secret@tuic.example.com:443?token=query-token#Token",
			want: map[string]any{
				"name": "Token", "type": "tuic", "server": "tuic.example.com", "token": "query-token",
			},
			absent: []string{"uuid", "password"},
		},
		{
			name: "upstream query credentials and optional fields",
			input: "tuic://ignored@query.example.com:443?uuid=" + uuid + "&password=secret" +
				"&sni=sni.example.com&alpn=h3,h2&congestion-control=bbr&udp-relay-mode=native" +
				"&request-timeout=5000&heartbeat-interval=9000&max-open-streams=16&max-idle-time=30" +
				"&reduce-rtt=1&tfo=true&allow-insecure=true&disable-sni=1#Query",
			want: map[string]any{
				"name": "Query", "uuid": uuid, "password": "secret", "sni": "sni.example.com",
				"alpn": []string{"h3", "h2"}, "congestion-controller": "bbr", "udp-relay-mode": "native",
				"request-timeout": 5000, "heartbeat-interval": 9000, "max-open-streams": 16,
				"max-idle-time": 30, "reduce-rtt": true, "tfo": true, "fast-open": true,
				"skip-cert-verify": true, "disable-sni": true,
			},
			absent: []string{"token"},
		},
		{
			name:  "IPv6 direct credentials and encoded name",
			input: "tuic://" + uuid + ":p%3A%2B%2520@[2001:db8::1]#%2520%2B+%E4%B8%AD",
			want: map[string]any{
				"server": "2001:db8::1", "port": 443, "uuid": uuid, "password": "p:+%20", "name": "%20+ 中",
			},
		},
		{
			name:  "UUID username with query password alias",
			input: "tuic://" + uuid + "@tuic.example.com?passwd=secret&congestionControl=cubic&udpRelayMode=quic",
			want: map[string]any{
				"uuid": uuid, "password": "secret", "congestion-controller": "cubic", "udp-relay-mode": "quic",
			},
		},
		{
			name:   "UUID without password is an upstream token",
			input:  "tuic://" + uuid + "@tuic.example.com",
			want:   map[string]any{"token": uuid, "name": "TUIC-tuic.example.com:443"},
			absent: []string{"uuid", "password"},
		},
		{
			name:   "query token replaces direct credentials",
			input:  "tuic://" + uuid + ":secret@tuic.example.com?auth_str=query-token",
			want:   map[string]any{"token": "query-token"},
			absent: []string{"uuid", "password"},
		},
		{
			name: "numeric prefixes aliases and bool semantics",
			input: "tuic://token@tuic.example.com?request-timeout=invalid&request_timeout=12ms" +
				"&heartbeatInterval=-3.5&max-open-streams=0&max_idle_time=%2B20s" +
				"&fast-open=invalid&fastOpen=off&tfo=on&insecure=yes&disableSni=TRUE&reduceRtt=on",
			want: map[string]any{
				"request-timeout": 12, "heartbeat-interval": -3, "max-open-streams": 0,
				"max-idle-time": 20, "fast-open": false, "tfo": true,
			},
			absent: []string{"skip-cert-verify", "disable-sni", "reduce-rtt"},
		},
		{
			name:   "IPv6 default name invalid optional number and empty bool",
			input:  "tuic://token@[2001:db8::1]:8443?requestTimeout=bad&fast-open=&tfo=&alpn=,,",
			want:   map[string]any{"name": "TUIC-2001:db8::1:8443", "port": 8443},
			absent: []string{"request-timeout", "fast-open", "tfo", "alpn"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseTUICLink(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestTUICInvalidLinks(t *testing.T) {
	for _, input := range []string{
		"http://bad", "tuic://@tuic.example.com:443", "tuic://token@:443",
		"tuic://uuid:secret@tuic.example.com:70000", "tuic://token@tuic.example.com:0",
		"tuic://token@tuic.example.com:invalid", "tuic://token@tuic.example.com:-1",
		"tuic://:secret@tuic.example.com", "tuic://tuic.example.com?uuid=uuid",
		"tuic://tuic.example.com?password=secret",
	} {
		if _, err := parseTUICLink(input); err == nil {
			t.Errorf("accepted invalid link %q", input)
		}
	}
}

func TestQUICLinkNormalization(t *testing.T) {
	for _, tc := range []struct {
		name  string
		input string
		parse func(string) (map[string]any, error)
		want  map[string]any
		node  string
	}{
		{
			name: "hysteria2", parse: parseHysteria2Link,
			input: "hy2://001@[2001:db8::1]?pinSHA256=" + strings.Repeat("AB", 32),
			want:  map[string]any{"password": "001", "fingerprint": strings.Repeat("ab", 32), "sni": "2001:db8::1"},
			node:  "Hysteria2 节点",
		},
		{
			name: "tuic", parse: parseTUICLink,
			input: "tuic://uuid:001@[2001:db8::1]?congestion_control=bbr",
			want:  map[string]any{"uuid": "uuid", "password": "001", "congestion-controller": "bbr"},
			node:  "TUIC-2001:db8::1:443",
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			node, err := tc.parse(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			normalized, err := flight.Normalize(node)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, normalized.Fields, tc.want)
			if normalized.Name != tc.node {
				t.Errorf("normalized name = %q, want %q", normalized.Name, tc.node)
			}
		})
	}
}
