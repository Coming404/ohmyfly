// 移植自 SubBoost vless.test.ts、vless-reality.test.ts 与 test/regression/core/*.test.ts @4015347（AGPL-3.0-only）。
// 这里的用例都是修补前本地实现与上游结果不同的输入。
package towerparse

import (
	"fmt"
	"net/url"
	"strings"
	"testing"
)

func TestSubBoostVLESSRegressions(t *testing.T) {
	const uuid = "11111111-1111-4111-8111-111111111111"
	reality := "vless://" + uuid + "@local.subboost.test:443?security=reality&pbk=" + strings.Repeat("A", 43)
	encrypted := reality + "&encryption=mlkem768x25519plus.native.0rtt.valid_token"
	tests := []struct {
		name   string
		input  string
		want   map[string]any
		absent map[string][]string
		err    bool
	}{
		// vless-reality.test.ts:18，显式给出空值或非法值时不输出，也不再按 MLKEM 加密推断。
		{name: "Reality empty support flag", input: encrypted + "&support-x25519mlkem768=", absent: map[string][]string{"reality-opts": {"support-x25519mlkem768"}}},
		{name: "Reality invalid support flag", input: encrypted + "&supportX25519MLKEM768=invalid", absent: map[string][]string{"reality-opts": {"support-x25519mlkem768"}}},
		// vless.test.ts:82，xhttp JSON 请求头里的数字转成字符串。
		{
			name:  "xhttp numeric JSON header",
			input: fmt.Sprintf("vless://%s@xhttp-alias.example.com:443?security=tls&type=xhttp&xhttpPath=/alias&headers=%s", uuid, url.QueryEscape(`{"bad":1}`)),
			want:  map[string]any{"xhttp-opts": map[string]any{"path": "/alias", "headers": map[string]any{"bad": "1"}}},
		},
		// vless.test.ts:459，obfsParam 不是 JSON 时整串当作 Host。
		{
			name:  "Shadowrocket obfsParam whole Host",
			input: fmt.Sprintf("vless://%s?obfs=websocket&tls=yes&flow=xtls-rprx-vision&obfsParam=NoColon%%7C%%3Abad%%7CHost%%3Acdn.example.com&path=%%2F#Flow", subBoostBase64URL("prefix:"+uuid+"@sr-flow.example.com:443")),
			want:  map[string]any{"server": "sr-flow.example.com", "network": "ws", "ws-opts": map[string]any{"path": "/", "headers": map[string]any{"Host": "NoColon|:bad|Host:cdn.example.com"}}},
		},
		// parser-edge-cases.test.ts:500，obfsParam 为 JSON 时按请求头解析。
		{
			name:  "Shadowrocket obfsParam JSON headers",
			input: fmt.Sprintf("vless://%s?obfs=websocket&obfsParam=%s&tls=1#SRJsonObject", subBoostBase64URL(uuid+"@sr-json-object.example.com:443"), url.QueryEscape(`{"Bad":1}`)),
			want:  map[string]any{"ws-opts": map[string]any{"path": "/", "headers": map[string]any{"Bad": "1"}}},
		},
		// parser-protocol-matrix.test.ts:521，pcs 原样保留。
		{
			name:  "pcs kept",
			input: fmt.Sprintf("vless://%s@vless-bool.example.com:443?allow_insecure=yes&pcs=one&pqv=two&alpn=h3,h2#Bool", uuid),
			want:  map[string]any{"skip-cert-verify": true, "pcs": "one", "pqv": "two", "alpn": []string{"h3", "h2"}},
		},
		// protocol-edge-matrix.test.ts:142，host 只有逗号时不写 Host。
		{
			name:   "ws empty host list",
			input:  fmt.Sprintf("vless://%s@plain-ws.example.com:443?type=ws&host=,,&path=%%3Fed%%3D64", uuid),
			want:   map[string]any{"network": "ws", "ws-opts": map[string]any{"path": "/", "max-early-data": 64}},
			absent: map[string][]string{"ws-opts": {"headers"}},
		},
		// parser-link-edge-cases.test.ts:113，type 只有空白时拒绝。
		{name: "blank type", input: fmt.Sprintf("vless://%s@empty-type.example.com:443?type=%%20#Bad", uuid), err: true},
		// vless.ts:246，没有名称时默认 "VLESS 节点"。
		{name: "default name", input: fmt.Sprintf("vless://%s@default.example.com:443", uuid), want: map[string]any{"name": "VLESS 节点"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseVLESSLink(tt.input)
			if tt.err {
				if err == nil {
					t.Fatalf("parseVLESSLink(%q) = %#v, want error", tt.input, got)
				}
				return
			}
			if err != nil {
				t.Fatalf("parseVLESSLink(%q): %v", tt.input, err)
			}
			assertMapSubset(t, got, tt.want)
			for parent, fields := range tt.absent {
				child, _ := got[parent].(map[string]any)
				assertMapAbsent(t, child, fields...)
			}
		})
	}
}
