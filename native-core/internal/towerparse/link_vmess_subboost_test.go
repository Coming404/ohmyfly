// Ported from SubBoost packages/core/src/parser/protocols/vmess.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/json"
	"fmt"
	"net/url"
	"testing"
)

func vmessJSONLink(t *testing.T, config map[string]any, fragment string) string {
	t.Helper()
	data, err := json.Marshal(config)
	if err != nil {
		t.Fatal(err)
	}
	return "vmess://" + subBoostBase64(string(data)) + fragment
}

func TestSubBoostVMessLinks(t *testing.T) {
	const uuid = "11111111-1111-1111-1111-111111111111"
	tests := []struct {
		name  string
		input string
		want  map[string]any
		err   bool
	}{
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:9.
		{
			name:  "base64 JSON and outer remark",
			input: vmessJSONLink(t, map[string]any{"v": "2", "ps": "Inner", "add": "vmess-json.example.com", "port": "443", "id": uuid, "aid": "0", "scy": "auto", "net": "ws", "tls": "tls", "path": "/graphql"}, "#Outer%20VMess#Remark"),
			want:  map[string]any{"name": "Outer VMess#Remark", "server": "vmess-json.example.com", "port": 443, "uuid": uuid, "network": "ws", "tls": true, "ws-opts": map[string]any{"path": "/graphql"}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:32.
		{
			name:  "Quantumult websocket",
			input: "vmess://" + subBoostBase64(`QX-VMess = vmess, qx-vmess.example.com, 443, auto, "`+uuid+`", obfs=wss, obfs-host=qx.example.com, obfs-path="/qx", tls-verification=false`),
			want:  map[string]any{"name": "QX-VMess", "server": "qx-vmess.example.com", "port": 443, "network": "ws", "tls": true, "servername": "qx.example.com", "skip-cert-verify": true},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:51.
		{
			name:  "URI grpc flags",
			input: fmt.Sprintf("vmess://%s@uri.example.com:443?type=grpc&security=tls&serviceName=svc&sni=edge.example.com&fp=chrome&alpn=h2,http/1.1&allowInsecure=1&packet-encoding=xudp&authenticated-length=1&global-padding=0#URI", uuid),
			want:  map[string]any{"name": "URI", "server": "uri.example.com", "port": 443, "uuid": uuid, "tls": true, "servername": "edge.example.com", "client-fingerprint": "chrome", "skip-cert-verify": true, "packet-encoding": "xudp", "authenticated-length": true, "global-padding": false, "alpn": []string{"h2", "http/1.1"}, "network": "grpc", "grpc-opts": map[string]any{"grpc-service-name": "svc"}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:83.
		{
			name:  "standard websocket",
			input: fmt.Sprintf("vmess://ws+tls:%s-0@standard.example.com:443/?host=cdn.example.com&path=%%2Fws%%3Fed%%3D1024#Standard", uuid),
			want:  map[string]any{"name": "Standard", "network": "ws", "tls": true, "ws-opts": map[string]any{"path": "/ws", "headers": map[string]any{"Host": "cdn.example.com"}, "max-early-data": 1024}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:85.
		{
			name:  "Kitsunebi websocket",
			input: fmt.Sprintf("vmess1://%s@kitsunebi.example.com:443/app?network=ws&host=kit.example.com&tls=1&sni=edge.example.com#Kit", uuid),
			want:  map[string]any{"name": "Kit", "network": "ws", "tls": true, "servername": "edge.example.com", "ws-opts": map[string]any{"path": "/app", "headers": map[string]any{"Host": "kit.example.com"}}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:106.
		{
			name:  "h2 transport",
			input: fmt.Sprintf("vmess://%s@h2.example.com:443?type=h2&security=tls&host=h2-a.example.com,h2-b.example.com&path=/h2#H2", uuid),
			want:  map[string]any{"name": "H2", "network": "h2", "h2-opts": map[string]any{"host": []string{"h2-a.example.com", "h2-b.example.com"}, "path": "/h2"}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:145.
		{
			name:  "Shadowrocket upgrade",
			input: fmt.Sprintf("vmess://%s?obfs=websocket&host=cdn.example.com&path=/sr%%3Fed%%3D256&sni=sni.example.com&allowInsecure=1#Shadowrocket", subBoostBase64URL("auto:"+uuid+"@shadowrocket.example.com:443")),
			want:  map[string]any{"name": "Shadowrocket", "network": "ws", "skip-cert-verify": true, "ws-opts": map[string]any{"path": "/sr", "headers": map[string]any{"Host": "cdn.example.com"}, "max-early-data": 256}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:149.
		{
			name:  "DingTalk HTTP User-Agent",
			input: vmessJSONLink(t, map[string]any{"ps": "HTTP", "add": "http-obfs.example.com", "port": 80, "id": uuid, "aid": 0, "scy": "auto", "net": "tcp", "type": "http", "host": "api.dingtalk.com", "path": "/a,/b", "method": "post", "headers": map[string]any{"Connection": "keep-alive"}}, ""),
			want:  map[string]any{"name": "HTTP", "network": "http", "http-opts": map[string]any{"method": "POST", "path": []string{"/a", "/b"}, "headers": map[string]any{"Host": []string{"api.dingtalk.com"}, "Connection": []string{"keep-alive"}, "User-Agent": []string{dingtalkUserAgent}}}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:190.
		{
			name:  "HTTP Edge headers",
			input: vmessJSONLink(t, map[string]any{"ps": "HTTP Edge", "add": "edge-obfs.example.com", "port": 80, "id": uuid, "net": "tcp", "type": "http", "host": "edge.example.com", "edge": "edge-header", "path": "/edge", "method": "m-search", "headers": map[string]any{"Host": []any{"custom.example.com"}, "User-Agent": 1}}, ""),
			want:  map[string]any{"network": "http", "http-opts": map[string]any{"method": "M-SEARCH", "headers": map[string]any{"Host": []string{"custom.example.com"}, "Edge": []string{"edge-header"}, "User-Agent": []string{"1"}}}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:229.
		{
			name:  "alternate fields and ECH",
			input: vmessJSONLink(t, map[string]any{"ps": "Camel", "add": "camel.example.com", "port": 443, "id": uuid, "net": "websocket", "path": "/", "tls": "tls", "ech": "", "packetEncoding": "xudp", "authenticatedLength": "false", "globalPadding": "true"}, ""),
			want:  map[string]any{"name": "Camel", "network": "ws", "packet-encoding": "xudp", "authenticated-length": false, "global-padding": true, "ech-opts": map[string]any{"enable": true}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:274.
		{
			name:  "Quantumult HTTP obfs",
			input: "vmess://" + subBoostBase64("QX HTTP = vmess, qx-http.example.com, 80, auto, \""+uuid+"\", obfs=http, obfs-header=Host:front.example.com, obfs-path=/front, aid=2, allowInsecure=true"),
			want:  map[string]any{"name": "QX HTTP", "server": "qx-http.example.com", "alterId": 2, "network": "http", "skip-cert-verify": true, "http-opts": map[string]any{"path": []string{"/front"}, "headers": map[string]any{"Host": []string{"front.example.com"}}}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:318.
		{
			name:  "DingTalk websocket",
			input: vmessJSONLink(t, map[string]any{"ps": "DingTalk WS", "add": "dingtalk-ws.example.com", "port": 443, "id": uuid, "aid": 0, "scy": "auto", "net": "ws", "tls": "tls", "host": "api.dingtalk.com", "path": "/"}, ""),
			want:  map[string]any{"name": "DingTalk WS", "network": "ws", "ws-opts": map[string]any{"path": "/", "headers": map[string]any{"Host": "api.dingtalk.com", "User-Agent": dingtalkUserAgent}}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:404.
		{
			name:  "URI grpc fallback path",
			input: fmt.Sprintf("vmess://%s@uri-grpc-path.example.com:443?type=grpc&security=tls&path=%%2Ffrom-path&ech=#UriGrpcPath", uuid),
			want:  map[string]any{"network": "grpc", "grpc-opts": map[string]any{"grpc-service-name": "from-path"}, "ech-opts": map[string]any{"enable": true}},
		},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:435.
		{
			name:  "unsupported transport",
			input: fmt.Sprintf("vmess://quic:%s-0@quic.example.com:443/?security=aes-128-gcm&type=video#Quic", uuid),
			err:   true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, _, err := parseVMessLink(tt.input)
			if tt.err {
				if err == nil {
					t.Fatalf("parseVMessLink(%q) succeeded", tt.input)
				}
				return
			}
			if err != nil {
				t.Fatalf("parseVMessLink(%q): %v", tt.input, err)
			}
			assertMapSubset(t, got, tt.want)
		})
	}
}

// Ported from SubBoost packages/core/src/parser/protocols/vmess.test.ts:486.
func TestSubBoostVMessMalformedLinks(t *testing.T) {
	const uuid = "11111111-1111-1111-1111-111111111111"
	tests := []struct {
		name  string
		input string
	}{
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:490.
		{"bad payload", "vmess://not-base64!"},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:493.
		{"bad quantumult", "vmess://" + subBoostBase64("Bad = vmess, only")},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:496.
		{"missing JSON server", "vmess://" + subBoostBase64(`{"ps":"Missing","port":443,"id":"`+uuid+`"}`)},
		// SubBoost packages/core/src/parser/protocols/vmess.test.ts:499.
		{"ECH without TLS", "vmess://" + subBoostBase64(`{"add":"bad.example.com","port":80,"id":"`+uuid+`","ech":"config"}`)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, _, err := parseVMessLink(tt.input); err == nil {
				t.Fatalf("parseVMessLink(%q) succeeded", tt.input)
			}
		})
	}
}

func TestSubBoostVMessURIQueryAliases(t *testing.T) {
	const uuid = "11111111-1111-1111-1111-111111111111"
	got, _, err := parseVMessLink(fmt.Sprintf("vmess://%s@uri-http.example.com:80?headerType=http&host=front.example.com&path=/a,/b&method=put&remark=%s", uuid, url.QueryEscape("URI HTTP")))
	if err != nil {
		t.Fatal(err)
	}
	assertMapSubset(t, got, map[string]any{"name": "URI HTTP", "network": "http", "http-opts": map[string]any{"method": "PUT", "path": []string{"/a", "/b"}, "headers": map[string]any{"Host": []string{"front.example.com"}}}})
}
