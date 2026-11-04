// Ported from SubBoost packages/core/src/parser/protocols/hysteria-protocols.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"strings"
	"testing"
)

func TestHysteriaLinks(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			name: "upstream v1 aliases and optional fields",
			input: "hy://hy.example.com?auth=secret&protocol=wechat-video&sni=sni.example.com" +
				"&allowInsecure=yes&alpn=h3,,h2&up=50&down=100&mport=1000-2000" +
				"&obfsParam=mask&obfs=salamander&fast-open=off#HY",
			want: map[string]any{
				"name": "HY", "type": "hysteria", "server": "hy.example.com", "port": 443,
				"protocol": "wechat-video", "auth-str": "secret", "sni": "sni.example.com",
				"skip-cert-verify": true, "alpn": []string{"h3", "h2"}, "up": "50", "down": "100",
				"ports": "1000-2000", "obfs": "mask", "_obfs": "salamander", "fast-open": false,
			},
		},
		{
			name:  "upstream v1 password and explicit false",
			input: "hysteria://hy.example.com:8443?password=secret&insecure=0",
			want: map[string]any{
				"name": "Hysteria hy.example.com:8443", "auth-str": "secret", "skip-cert-verify": false,
			},
		},
		{
			name:   "IPv6 default port and optional auth",
			input:  "hy://[2001:db8::1]",
			want:   map[string]any{"name": "Hysteria 2001:db8::1:443", "server": "2001:db8::1", "port": 443, "protocol": "udp"},
			absent: []string{"auth-str", "sni", "skip-cert-verify"},
		},
		{
			name: "alias precedence empty fast-open and raw hopping ports",
			input: "hysteria://hy.example.com?auth_str=p%2B%25&upmbps=10&up=20&downmbps=30&down=40" +
				"&mport=9000%3B9001%2F9002&ports=443&insecure=invalid&allowInsecure=off" +
				"&fast-open=&fast_open=true&tfo=on&obfs=",
			want: map[string]any{
				"auth-str": "p+%", "up": "10", "down": "30", "ports": "9000;9001/9002",
				"skip-cert-verify": false, "tfo": true, "_obfs": "",
			},
			absent: []string{"fast-open"},
		},
		{
			name:  "name is form-decoded exactly once",
			input: "hy://hy.example.com#%2520%2B+%E4%B8%AD",
			want:  map[string]any{"name": "%20+ 中"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseHysteriaLink(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestHysteria2Links(t *testing.T) {
	pin := strings.TrimSuffix(strings.Repeat("AB:", 32), ":")
	cases := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
	}{
		{
			// 上游原样写 fp=chrome、补 " mbps"；mihomo 读不了，这里丢掉非证书指纹、单位改大写。
			name: "upstream query password IPv6 and TLS",
			input: "hy2://[2001:db8::1]:8443?password=secret&sni=sni.example.com" +
				"&allow_insecure=true&alpn=h3,h2&fp=chrome&up=50&down=100mbps" +
				"&mldsa65Seed=seed&hopInterval=15-30#IPv6",
			want: map[string]any{
				"name": "IPv6", "type": "hysteria2", "server": "2001:db8::1", "port": 8443,
				"password": "secret", "sni": "sni.example.com", "skip-cert-verify": true,
				"alpn": []string{"h3", "h2"}, "up": "50 Mbps", "down": "100 Mbps",
				"hop-interval": "15-30", "mldsa65-seed": "seed",
			},
			absent: []string{"fingerprint"},
		},
		{
			name: "upstream authority range and salamander",
			input: "hysteria2://secret@hy2.example.com:1000-1002?obfs=salamander" +
				"&obfs_password=mask&upmbps=20&downmbps=30&hop-interval=10#Ports",
			want: map[string]any{
				"name": "Ports", "server": "hy2.example.com", "port": 1000, "password": "secret",
				"ports": "1000-1002", "obfs": "salamander", "obfs-password": "mask",
				"up": "20 Mbps", "down": "30 Mbps", "hop-interval": 10,
			},
		},
		{
			name:  "upstream query ports and auth",
			input: "hysteria2://hy2.example.com?auth=secret&mport=2000,2001",
			want: map[string]any{
				"name": "Hysteria2 节点", "server": "hy2.example.com", "port": 2000,
				"password": "secret", "ports": "2000,2001",
			},
		},
		{
			name: "upstream IPv6 trailing slashes and hop fallback",
			input: "hysteria2://secret@[2001:db8::2]///?obfs=none&alpn=,," +
				"&up=10kbps&down=20&hop-interval=0&hop_interval=5",
			want: map[string]any{
				"name": "Hysteria2 节点", "server": "2001:db8::2", "port": 443, "password": "secret",
				"sni": "2001:db8::2", "up": "10 Kbps", "down": "20 Mbps", "hop-interval": 5,
			},
			absent: []string{"obfs", "alpn"},
		},
		{
			name: "upstream auth alias and reversed hop fallback",
			input: "hysteria2://alias.example.com?auth_str=query-secret&ports=3000-3002" +
				"&hop-interval=30-15&hopInterval=20-30",
			want: map[string]any{
				"server": "alias.example.com", "password": "query-secret", "port": 3000,
				"ports": "3000-3002", "hop-interval": "20-30",
			},
		},
		{
			name: "ports separators prefer 443 and canonical hop range",
			input: "hy2://secret@hy2.example.com?ports=9999;440-445/8443" +
				"&hop-interval=0005-0010&up=1gbps&upmbps=2&down=3&downmbps=4",
			want: map[string]any{
				"port": 443, "ports": "9999,440-445,8443", "hop-interval": "5-10", "up": "1 Gbps", "down": "3 Mbps",
			},
		},
		{
			name:  "explicit port wins over query ports",
			input: "hy2://secret@hy2.example.com:8443?ports=2000-2002",
			want:  map[string]any{"port": 8443, "ports": "2000-2002"},
		},
		{
			name:  "invalid segments skipped and first valid range chosen",
			input: "hy2://secret@hy2.example.com:70000,0,2000-1999,2005-2008",
			want:  map[string]any{"port": 2005, "ports": "70000,0,2000-1999,2005-2008"},
		},
		{
			name:   "invalid port spec retains upstream 443 fallback",
			input:  "hy2://secret@hy2.example.com:invalid?hop-interval=bad&hop_interval=-1",
			want:   map[string]any{"port": 443, "ports": "invalid"},
			absent: []string{"hop-interval"},
		},
		{
			name: "gecko packet sizes ECH and normalized certificate pin",
			input: "hy2://secret@hy2.example.com?obfs=GECKO&obfsPassword=mask" +
				"&obfs_min_packet_size=0&obfs-max-packet-size=9007199254740991&ech=ech.example.com&pinSHA256=" + pin,
			want: map[string]any{
				"obfs": "gecko", "obfs-password": "mask", "obfs-min-packet-size": 0,
				"obfs-max-packet-size": 9007199254740991, "fingerprint": strings.Repeat("ab", 32),
				"ech-opts": map[string]any{"enable": true, "query-server-name": "ech.example.com"},
			},
		},
		{
			name: "userinfo password wins and is decoded once",
			input: "hysteria2://a%3Ab%2B%2520@hy2.example.com?password=query&insecure=0" +
				"&allowInsecure=true#%2520%2B+%E4%B8%AD",
			want: map[string]any{"password": "a:b+%20", "name": "%20+ 中", "skip-cert-verify": true},
		},
		{
			name:   "empty ECH non-hex pin dropped and strict insecure flag",
			input:  "hy2://secret@hy2.example.com?ech=&pinSHA256=chrome&insecure=yes",
			want:   map[string]any{"ech-opts": map[string]any{"enable": true}},
			absent: []string{"skip-cert-verify", "fingerprint"},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parseHysteria2Link(tc.input)
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestHysteriaInvalidLinks(t *testing.T) {
	for _, protocol := range []struct {
		name  string
		parse func(string) (map[string]any, error)
		links []string
	}{
		{
			name: "hysteria", parse: parseHysteriaLink,
			links: []string{
				"http://bad", "hysteria://:443", "hysteria://hy.example.com:70000",
				"hy://hy.example.com:0", "hy://hy.example.com:invalid", "hy://hy.example.com:-1",
			},
		},
		{
			name: "hysteria2", parse: parseHysteria2Link,
			links: []string{
				"http://bad", "hysteria2://@hy2.example.com:443", "hy2://secret@:443",
				"hysteria2://secret@hy2.example.com:70000", "hy2://secret@hy2.example.com:0",
				"hysteria2://secret@hy2.example.com:443?obfs=salamander",
				"hy2://secret@hy2.example.com?obfs=unsupported&obfs-password=mask",
				"hy2://secret@hy2.example.com?obfs-min-packet-size=",
				"hy2://secret@hy2.example.com?obfs-min-packet-size=-1",
				"hy2://secret@hy2.example.com?obfs-max-packet-size=1.5",
				"hy2://secret@hy2.example.com?obfs_max_packet_size=9007199254740992",
			},
		},
	} {
		t.Run(protocol.name, func(t *testing.T) {
			for _, input := range protocol.links {
				if _, err := protocol.parse(input); err == nil {
					t.Errorf("accepted invalid link %q", input)
				}
			}
		})
	}
}
