// 移植自 SubBoost vmess.test.ts 与 test/regression/core/*.test.ts @4015347（AGPL-3.0-only）。
// 这里的用例都是重写前本地实现与上游结果不同的输入。
package towerparse

import (
	"fmt"
	"testing"
)

func TestSubBoostVMessRegressions(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	tests := []struct {
		name   string
		input  string
		want   map[string]any
		absent []string
		err    bool
	}{
		// vmess.test.ts:358，Shadowrocket 的 UUID 本身含 "-"，不能被当作 alterId 截掉；obfs=wss 只开 TLS。
		{
			name:  "Shadowrocket full UUID and wss",
			input: fmt.Sprintf("vmess://%s?obfs=wss&remarks=Query%%20Name&allow_insecure=0", subBoostBase64URL("auto:"+uuid+"@shadow-remark.example.com:443")),
			want:  map[string]any{"name": "Query Name", "server": "shadow-remark.example.com", "uuid": uuid, "network": "tcp", "tls": true},
		},
		// vmess.test.ts:466，ws 的 edge 写入 headers.Edge。
		{
			name:  "JSON ws Edge header",
			input: vmessJSONLink(t, map[string]any{"ps": "Edge WS", "add": "edge-ws.example.com", "port": 443, "id": uuid, "aid": 0, "net": "ws", "tls": "tls", "edge": "edge-header", "path": "/"}, ""),
			want:  map[string]any{"network": "ws", "ws-opts": map[string]any{"path": "/", "headers": map[string]any{"Edge": "edge-header"}}},
		},
		// parser-generator-refresh-regressions.test.ts:140，net 为对象时按 tcp 处理。
		{
			name:  "JSON object network",
			input: vmessJSONLink(t, map[string]any{"ps": "Object Network", "add": "object-network.example.com", "port": 443, "id": uuid, "net": map[string]any{"raw": "ws"}}, ""),
			want:  map[string]any{"network": "tcp"},
		},
		// parser-matrix.test.ts:327，多个 host 时 ws 只取第一个，servername 也取第一个。
		{
			name:  "URI ws first host and Edge",
			input: fmt.Sprintf("vmess://%s@uri-ws.example.com:443?type=websocket&path=/ws?ed=2048&host=a.example.com,b.example.com&edge=edge&ua=agent#WS", uuid),
			want:  map[string]any{"network": "ws", "servername": "a.example.com", "ws-opts": map[string]any{"path": "/ws", "max-early-data": 2048, "headers": map[string]any{"Host": "a.example.com", "Edge": "edge"}}},
		},
		// parser-preprocess-edge-cases.test.ts:83，Quantumult 配置逗号后没有空格。
		{
			name:  "Quantumult without spaces",
			input: "vmess://" + subBoostBase64("QX=vmess,quantum.example.com,443,auto,"+uuid+",obfs=wss,obfs-header=Host: q.example.com,obfs-path=/q,tls-verification=false"),
			want:  map[string]any{"name": "QX", "network": "ws", "tls": true, "skip-cert-verify": true, "ws-opts": map[string]any{"path": "/q", "headers": map[string]any{"Host": "q.example.com"}}},
		},
		// parser-matrix.test.ts:613 的输入，ws path 为 / 且没有请求头时上游不输出 ws-opts。
		{
			name:   "bare ws omits options",
			input:  vmessJSONLink(t, map[string]any{"ps": "Bare WS", "add": "bare-ws.example.com", "port": 443, "id": uuid, "aid": 0, "net": "ws", "path": "/"}, ""),
			want:   map[string]any{"network": "ws"},
			absent: []string{"ws-opts"},
		},
		// JSON 里的 null 视为缺失。
		{
			name:  "JSON null server",
			input: vmessJSONLink(t, map[string]any{"add": nil, "port": 443, "id": uuid}, ""),
			err:   true,
		},
		// parser-link-edge-cases.test.ts:125，std vmess 链接的路径后必须跟查询串。
		{
			name:  "standard path without query",
			input: fmt.Sprintf("vmess://tcp:%s-0@std-bad.example.com:443/path-without-query", uuid),
			err:   true,
		},
		// parser-protocol-matrix.test.ts:478，quic 不在支持的传输层里。
		{
			name:  "standard quic",
			input: fmt.Sprintf("vmess://quic+tls:%s-0@quic.example.com:443?type=none&security=aes-128-gcm&key=secret&sni=front.example.com#Quic", uuid),
			err:   true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, _, err := parseVMessLink(tt.input)
			if tt.err {
				if err == nil {
					t.Fatalf("parseVMessLink(%q) = %#v, want error", tt.input, got)
				}
				return
			}
			if err != nil {
				t.Fatalf("parseVMessLink(%q): %v", tt.input, err)
			}
			assertMapSubset(t, got, tt.want)
			assertMapAbsent(t, got, tt.absent...)
		})
	}
}

// 别名冲突时上游按固定顺序取值（packet-encoding 优先于 packetEncoding），结果不能随 map 遍历顺序变化。
func TestVMessConflictingAliasesAreDeterministic(t *testing.T) {
	input := vmessJSONLink(t, map[string]any{
		"add": "node.example", "port": 443, "id": "550e8400-e29b-41d4-a716-446655440000", "net": "tcp",
		"authenticated-length": false, "authenticatedLength": true, "packet-encoding": "xudp", "packetEncoding": "packet",
	}, "")
	for range 20 {
		got, _, err := parseVMessLink(input)
		if err != nil {
			t.Fatal(err)
		}
		assertMapSubset(t, got, map[string]any{"authenticated-length": false, "packet-encoding": "xudp"})
	}
}
