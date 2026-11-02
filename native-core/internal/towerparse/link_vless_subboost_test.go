// Ported from SubBoost packages/core/src/parser/protocols/vless.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"net/url"
	"testing"
)

func TestSubBoostVLESSLinks(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	encoded := subBoostBase64URL("prefix:" + uuid + "@shadowrocket.example.com:443")
	tests := []struct {
		name  string
		input string
		want  map[string]any
		err   bool
	}{
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:10.
		{
			name:  "http transport with domain fronting",
			input: fmt.Sprintf("vless://%s@vless-http.example.com:443?security=tls&type=http&host=front.example.com,alt.example.com&path=/a,/b&method=post&sni=edge.example.com&allow-insecure=1&alpn=h2,http/1.1&packet-encoding=xudp#HTTP", uuid),
			want: map[string]any{
				"name": "HTTP", "type": "vless", "server": "vless-http.example.com", "port": 443,
				"uuid": uuid, "tls": true, "servername": "edge.example.com", "skip-cert-verify": true,
				"alpn": []string{"h2", "http/1.1"}, "packet-encoding": "xudp", "network": "http",
				"http-opts": map[string]any{"method": "POST", "path": []string{"/a", "/b"}, "headers": map[string]any{"Host": []string{"front.example.com", "alt.example.com"}}},
			},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:38.
		{
			name:  "grpc transport",
			input: fmt.Sprintf("vless://%s@grpc.example.com:443?security=tls&type=grpc&serviceName=svc&mode=gun&authority=authority.example.com#GRPC", uuid),
			want:  map[string]any{"name": "GRPC", "network": "grpc", "grpc-opts": map[string]any{"grpc-service-name": "svc", "_grpcType": "gun", "_grpcAuthority": "authority.example.com"}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:45.
		{
			name:  "xhttp flat aliases",
			input: fmt.Sprintf("vless://%s@xhttp.example.com:443?security=tls&type=xhttp&path=/x&host=cdn.example.com&xhttp-mode=packet-up&xhttp-headers=%s&no-grpc-header=1&x-padding-bytes=100-200&sc-max-each-post-bytes=2048&max-concurrency=4&download-path=/download&download-host=download.example.com&download-headers=%s#XHTTP", uuid, url.QueryEscape("User-Agent:SubBoost|X-Test:yes"), url.QueryEscape(`{"Accept":"application/octet-stream"}`)),
			want: map[string]any{"name": "XHTTP", "network": "xhttp", "xhttp-opts": map[string]any{
				"path": "/x", "host": "cdn.example.com", "mode": "packet-up", "headers": map[string]any{"User-Agent": "SubBoost", "X-Test": "yes"}, "no-grpc-header": true,
				"x-padding-bytes": "100-200", "sc-max-each-post-bytes": 2048, "reuse-settings": map[string]any{"max-concurrency": "4"},
				"download-settings": map[string]any{"path": "/download", "host": "download.example.com", "headers": map[string]any{"Accept": "application/octet-stream"}},
			}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:107.
		{
			name:  "shadowrocket websocket",
			input: fmt.Sprintf("vless://%s?obfs=websocket&tls=1&xtls=2&obfsParam=cdn.example.com&path=/ws%%3Fed%%3D512#Shadowrocket", encoded),
			want:  map[string]any{"name": "Shadowrocket", "server": "shadowrocket.example.com", "uuid": uuid, "tls": true, "flow": "xtls-rprx-vision", "client-fingerprint": "chrome", "network": "ws", "ws-opts": map[string]any{"path": "/ws", "headers": map[string]any{"Host": "cdn.example.com"}, "max-early-data": 512}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:147.
		{
			name:  "strict compound ech",
			input: fmt.Sprintf("vless://%s?obfs=xhttp&tls=1&peer=front.example.com&path=%%2Fxhttp&ech=%s#ShadowrocketECH", subBoostBase64URL(":"+uuid+"@vless-ech.example.com:443"), url.QueryEscape("cloudflare-ech.com+https://203.0.113.53/dns-query")),
			want:  map[string]any{"tls": true, "servername": "front.example.com", "client-fingerprint": "chrome", "network": "xhttp", "ech-opts": map[string]any{"enable": true, "query-server-name": "cloudflare-ech.com"}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:214.
		{
			name:  "implicit reality from public key",
			input: fmt.Sprintf("vless://%s?tls=1&pbk=%s#ImplicitReality", subBoostBase64URL(uuid+"@shadow-reality.example.com:443"), "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
			want:  map[string]any{"tls": true, "client-fingerprint": "chrome", "reality-opts": map[string]any{"public-key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:251.
		{
			name:  "h2 defaults",
			input: fmt.Sprintf("vless://%s@h2-default.example.com:443?security=tls&type=h2#H2Default", uuid),
			want:  map[string]any{"name": "H2Default", "network": "h2", "h2-opts": map[string]any{"path": "/"}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:267.
		{
			name:  "http upgrade",
			input: fmt.Sprintf("vless://%s@upgrade.example.com:443?type=httpupgrade&security=tls&host=cdn.example.com&path=/upgrade#Upgrade", uuid),
			want:  map[string]any{"name": "Upgrade", "network": "ws", "ws-opts": map[string]any{"path": "/upgrade", "v2ray-http-upgrade": true, "v2ray-http-upgrade-fast-open": true}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:324.
		{
			name:  "minimal tcp and ech",
			input: fmt.Sprintf("vless://%s@ech.example.com:443?security=tls&ech=#ECH", uuid),
			want:  map[string]any{"name": "ECH", "tls": true, "network": "tcp", "ech-opts": map[string]any{"enable": true}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:374.
		{
			name:  "shadowrocket json headers",
			input: fmt.Sprintf("vless://%s?obfs=websocket&obfsParam=%s&path=/ws#SRJSON", encoded, url.QueryEscape(`{"Host":"json.example.com","User-Agent":"UA"}`)),
			want:  map[string]any{"name": "SRJSON", "network": "ws", "ws-opts": map[string]any{"path": "/ws", "headers": map[string]any{"Host": "json.example.com", "User-Agent": "UA"}}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:396.
		{
			name:  "plain websocket without headers",
			input: fmt.Sprintf("vless://%s@plain-ws.example.com:443?type=websocket&alpn=,,&path=/ws#PlainWS", uuid),
			want:  map[string]any{"name": "PlainWS", "network": "ws", "ws-opts": map[string]any{"path": "/ws"}},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:451.
		{
			name:  "ipv6 authority",
			input: fmt.Sprintf("vless://%s@[2001:db8::1]:443?security=tls&type=ws&host=ipv6.example.com#IPv6", uuid),
			want:  map[string]any{"name": "IPv6", "server": "2001:db8::1", "port": 443, "network": "ws"},
		},
		// SubBoost packages/core/src/parser/protocols/vless.test.ts:512.
		{
			name:  "unsupported transport",
			input: fmt.Sprintf("vless://%s@bad.example.com:443?type=kcp#Bad", uuid),
			err:   true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseVLESSLink(tt.input)
			if tt.err {
				if err == nil {
					t.Fatalf("parseVLESSLink(%q) succeeded", tt.input)
				}
				return
			}
			if err != nil {
				t.Fatalf("parseVLESSLink(%q): %v", tt.input, err)
			}
			assertMapSubset(t, got, tt.want)
		})
	}
}

// Ported from SubBoost packages/core/src/parser/protocols/vless-xhttp.test.ts:9.
func TestSubBoostVLESSXHTTPExtra(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	extra := `{"path":"/ignored","host":"ignored","mode":"invalid-but-outer-wins","headers":{"X-Test":"a%2Fb+value"},"noGRPCHeader":false,"xPaddingBytes":"100-200","xPaddingObfsMode":true,"xPaddingKey":"padding","xPaddingHeader":"X-Pad","xPaddingPlacement":"header","xPaddingMethod":"tokenish","uplinkHTTPMethod":"POST","sessionIDPlacement":"header","sessionIDKey":"X-Session","sessionIDTable":"hex","sessionIDLength":"16-32","seqPlacement":"query","seqKey":"seq","uplinkDataPlacement":"body","uplinkDataKey":"payload","uplinkChunkSize":"1024-2048","scMaxEachPostBytes":"100000-300000","scMinPostsIntervalMs":"10-30","xmux":{"maxConcurrency":0,"maxConnections":"2-4","cMaxReuseTimes":0,"hMaxRequestTimes":"600-900","hMaxReusableSecs":"1800-3000","hKeepAlivePeriod":-1},"noSSEHeader":true}`
	input := fmt.Sprintf("vless://%s@local.subboost.test:443?type=xhttp&security=tls&path=/upload&host=local.subboost.test&mode=packet-up&extra=%s", uuid, url.QueryEscape(extra))
	got, err := parseVLESSLink(input)
	if err != nil {
		t.Fatal(err)
	}
	options := got["xhttp-opts"].(map[string]any)
	assertMapSubset(t, options, map[string]any{
		"path": "/upload", "host": "local.subboost.test", "mode": "packet-up", "no-grpc-header": false,
		"x-padding-bytes": "100-200", "x-padding-obfs-mode": true, "x-padding-key": "padding", "x-padding-header": "X-Pad",
		"x-padding-placement": "header", "x-padding-method": "tokenish", "uplink-http-method": "POST", "session-placement": "header",
		"session-key": "X-Session", "session-table": "hex", "session-length": "16-32", "seq-placement": "query", "seq-key": "seq",
		"uplink-data-placement": "body", "uplink-data-key": "payload", "uplink-chunk-size": "1024-2048",
		"sc-max-each-post-bytes": "100000-300000", "sc-min-posts-interval-ms": "10-30",
		"reuse-settings": map[string]any{"max-concurrency": "0", "max-connections": "2-4", "c-max-reuse-times": "0", "h-max-request-times": "600-900", "h-max-reusable-secs": "1800-3000", "h-keep-alive-period": -1},
	})
	tests := []struct {
		name  string
		input string
	}{
		// SubBoost packages/core/src/parser/protocols/vless-xhttp.test.ts:62.
		{"invalid extra type", fmt.Sprintf("vless://%s@xhttp.example.com:443?type=xhttp&extra=%s", uuid, url.QueryEscape(`[]`))},
		// SubBoost packages/core/src/parser/protocols/vless-xhttp.test.ts:66.
		{"invalid range", fmt.Sprintf("vless://%s@xhttp.example.com:443?type=xhttp&extra=%s", uuid, url.QueryEscape(`{"scMaxEachPostBytes":"bad"}`))},
		// SubBoost packages/core/src/parser/protocols/vless-xhttp.test.ts:83.
		{"invalid mode", fmt.Sprintf("vless://%s@xhttp.example.com:443?type=xhttp&extra=%s&mode=broken", uuid, url.QueryEscape(`{}`))},
		// SubBoost packages/core/src/parser/protocols/vless-xhttp.test.ts:93.
		{"reserved session table", fmt.Sprintf("vless://%s@xhttp.example.com:443?type=xhttp&extra=%s", uuid, url.QueryEscape(`{"sessionIDTable":"uuid","sessionIDLength":20}`))},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := parseVLESSLink(tt.input); err == nil {
				t.Fatalf("parseVLESSLink(%q) succeeded", tt.input)
			}
		})
	}
}

// Ported from SubBoost packages/core/src/parser/protocols/vless-reality.test.ts:11.
func TestSubBoostVLESSRealitySupport(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	const publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
	base := fmt.Sprintf("vless://%s@local.subboost.test:443?security=reality&pbk=%s", uuid, publicKey)
	tests := []struct {
		name string
		link string
		want any
	}{
		// SubBoost packages/core/src/parser/protocols/vless-reality.test.ts:11.
		{"support true", base + "&support-x25519mlkem768=true", true},
		// SubBoost packages/core/src/parser/protocols/vless-reality.test.ts:12.
		{"support false", base + "&support_x25519mlkem768=0", false},
		// SubBoost packages/core/src/parser/protocols/vless-reality.test.ts:28.
		{"encrypted default", base + "&encryption=mlkem768x25519plus.native.0rtt.valid_token", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseVLESSLink(tt.link)
			if err != nil {
				t.Fatal(err)
			}
			reality := got["reality-opts"].(map[string]any)
			if reality["support-x25519mlkem768"] != tt.want {
				t.Fatalf("support=%#v, want %#v", reality["support-x25519mlkem768"], tt.want)
			}
		})
	}
}
