// Ported from SubBoost packages/core/src/parser/protocols/ss.test.ts @4015347 (AGPL-3.0-only).
// Regression cases ported from test/regression/core/parser-{edge-cases,matrix,protocol-matrix,protocol-edge-cases,and-template-regressions}.test.ts @4015347.
package towerparse

import (
	"net/url"
	"reflect"
	"testing"
)

func TestSubBoostSSLinks(t *testing.T) {
	b64 := subBoostBase64
	user := b64("aes-128-gcm:secret")
	testSimpleProxyLinks(t, parseSSLink, []simpleProxyTestCase{
		// ss.test.ts:10.
		{name: "legacy base64", input: "ss://" + b64("aes-128-gcm:secret@ss.example.com:8388") + "#Full", want: map[string]any{"name": "Full", "type": "ss", "server": "ss.example.com", "port": 8388, "cipher": "aes-128-gcm", "password": "secret", "udp": true}},
		// ss.test.ts:11.
		{name: "SIP002 IPv6 plugin", input: "ss://aes-256-gcm:pass@[2001:db8::1]:8388?plugin=" + url.QueryEscape("v2ray-plugin;mode=websocket;mux=1;tls=0") + "&uot=1&tfo=yes#IPv6", want: map[string]any{"name": "IPv6", "server": "2001:db8::1", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "mux": true, "tls": false}, "udp-over-tcp": true, "tfo": true}},
		// ss.test.ts:39.
		{name: "v2ray plugin JSON", input: "ss://" + user + "@ss-json.example.com:8388?v2ray-plugin=" + url.QueryEscape(b64(`{"mode":"websocket","host":"cdn.example.com","mux":"0"}`)) + "#JSON", want: map[string]any{"name": "JSON", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "host": "cdn.example.com", "mux": false}}},
		// ss.test.ts:56.
		{name: "empty flags", input: "ss://aes-128-gcm:secret@bool.example.com:8388?uot=&tfo=#Bool", want: map[string]any{"name": "Bool", "server": "bool.example.com", "udp-over-tcp": true, "tfo": true}},
		// ss.test.ts:57.
		{name: "invalid flags", input: "ss://aes-128-gcm:secret@flags.example.com:8388?uot=maybe&tfo=0#Flags", want: map[string]any{"name": "Flags", "server": "flags.example.com"}, absent: []string{"udp-over-tcp", "tfo"}},
		// ss.test.ts:58.
		{name: "invalid JSON", input: "ss://aes-128-gcm:secret@plain.example.com:8388?v2ray-plugin=" + url.QueryEscape("not-json") + "#Plain", want: map[string]any{"name": "Plain", "server": "plain.example.com"}, absent: []string{"plugin"}},
		// ss.test.ts:61.
		{name: "empty JSON", input: "ss://aes-128-gcm:secret@empty-json.example.com:8388?v2ray-plugin=   #EmptyJSON", want: map[string]any{"name": "EmptyJSON"}, absent: []string{"plugin"}},
		// ss.test.ts:135.
		{name: "escaped plugin", input: "ss://" + user + "@escaped.example.com:8388?plugin=" + url.QueryEscape(`obfs-local;obfs=tls;obfs-host=cdn\;edge.example.com;flag;empty=`) + "&uot=0&tfo=on#Escaped", want: map[string]any{"name": "Escaped", "plugin": "obfs", "plugin-opts": map[string]any{"mode": "tls", "host": "cdn;edge.example.com"}, "tfo": true}, absent: []string{"udp-over-tcp"}},
		// ss.test.ts:150.
		{name: "empty plugin segments", input: "ss://" + user + "@empty-plugin.example.com:8388?plugin=" + url.QueryEscape("obfs-local;%20;empty=") + "#EmptyPlugin", want: map[string]any{"name": "EmptyPlugin", "plugin": "obfs"}},
		// ss.test.ts:158.
		{name: "full IPv6 base64", input: "ss://" + b64("aes-128-gcm:secret@[2001:db8::2]:8388") + "#FullIPv6", want: map[string]any{"name": "FullIPv6", "server": "2001:db8::2", "port": 8388}},
		// ss.test.ts:165-171.
		{name: "missing port", input: "ss://" + b64("aes-128-gcm:secret@ss.example.com"), message: "missing ss port"},
		{name: "missing cipher", input: "ss://" + b64("bad@ss.example.com:8388"), message: "invalid ss credentials"},
		{name: "outer missing cipher", input: "ss://" + b64("bad") + "@outer.example.com:8388", message: "invalid ss credentials"},
		{name: "not base64", input: "ss://not-base64", message: "invalid ss payload"},
		{name: "port out of range", input: "ss://" + b64("aes-128-gcm:secret@ss.example.com:70000"), message: "invalid ss port"},
	})
}

func TestSubBoostSSRegressionLinks(t *testing.T) {
	b64 := subBoostBase64
	user := b64("aes-128-gcm:pw")
	testSimpleProxyLinks(t, parseSSLink, []simpleProxyTestCase{
		// parser-edge-cases.test.ts:271.
		{name: "escaped backslash and empty key", input: "ss://aes-128-gcm:pw@[2001:db8::8]:8388?plugin=" + url.QueryEscape(`v2ray-plugin;path=\z;=ignored;empty=;`) + "&uot=off&tfo=#IPv6", want: map[string]any{"name": "IPv6", "server": "2001:db8::8", "tfo": true, "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"path": `\z`, "empty": true}}, absent: []string{"udp-over-tcp"}},
		// parser-edge-cases.test.ts:283.
		{name: "base64 IPv6 without name", input: "ss://" + b64("aes-128-gcm:pw@[2001:db8::9]:8388"), want: map[string]any{"server": "2001:db8::9"}},
		// parser-edge-cases.test.ts:286.
		{name: "v2ray JSON string bools", input: "ss://" + user + "@json-plugin.example.com:8388?v2ray-plugin=" + url.QueryEscape(b64(`{"mux":"false","tls":"on"}`)), want: map[string]any{"plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mux": false, "tls": true}}},
		// parser-edge-cases.test.ts:296.
		{name: "empty obfs segments", input: "ss://" + user + "@plugin-empty.example.com:8388?plugin=" + url.QueryEscape("obfs-local;;;"), want: map[string]any{"plugin": "obfs"}, absent: []string{"plugin-opts"}},
		// parser-edge-cases.test.ts:303-306.
		{name: "port zero", input: "ss://" + b64("aes-128-gcm:pw@bad.example.com:0"), message: "invalid ss port"},
		{name: "outer missing port", input: "ss://" + user + "@missing-port.example.com#Bad", message: "missing ss port"},
		{name: "invalid base64 text", input: "ss://not-valid-base64", message: "invalid ss payload"},
		// parser-matrix.test.ts:497.
		{name: "IPv6 flags", input: "ss://" + user + "@[2001:db8::1]:8388?uot=&tfo=1#IPv6", want: map[string]any{"name": "IPv6", "server": "2001:db8::1", "udp-over-tcp": true, "tfo": true}},
		// parser-matrix.test.ts:503.
		{name: "v2ray JSON tls", input: "ss://" + user + "@plugin.example.com:8388?v2ray-plugin=" + url.QueryEscape(b64(`{"tls":"1","mode":"websocket"}`)), want: map[string]any{"plugin": "v2ray-plugin", "plugin-opts": map[string]any{"tls": true, "mode": "websocket"}}},
		// parser-matrix.test.ts:507-509.
		{name: "credentials without colon", input: "ss://" + b64("missing-colon") + "@ss.example.com:8388", message: "invalid ss credentials"},
		{name: "credentials only", input: "ss://" + user, message: "invalid ss payload"},
		// parser-protocol-matrix.test.ts:117.
		{name: "escaped plain credentials", input: "ss://aes-128-gcm:plain%3Apw@plain.example.com:8388?plugin=" + url.QueryEscape(`v2ray-plugin;path=/a\;b;tls=1;mux=no;raw\=key=value`) + "&uot=&tfo=bad#Plain", want: map[string]any{"name": "Plain", "server": "plain.example.com", "password": "plain:pw", "udp-over-tcp": true, "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"path": "/a;b", "tls": true, "mux": false}}, absent: []string{"tfo"}},
		// parser-protocol-matrix.test.ts:129.
		{name: "raw JSON after base64 payload", input: "ss://" + b64("aes-128-gcm:pw@payload.example.com:8388") + "?v2ray-plugin=" + url.QueryEscape(`{"mode":"websocket","tls":1}`), want: map[string]any{"server": "payload.example.com", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "tls": true}}},
		// parser-protocol-matrix.test.ts:132-134.
		{name: "percent garbage", input: "ss://%%%%", message: "invalid URL escape"},
		{name: "payload without port", input: "ss://" + b64("aes-128-gcm:pw@payload.example.com"), message: "missing ss port"},
		{name: "payload without password", input: "ss://" + b64("aes-128-gcm@payload.example.com:8388"), message: "invalid ss credentials"},
		// parser-protocol-edge-cases.test.ts:110.
		{name: "v2ray obfs aliases", input: "ss://" + b64("aes-128-gcm:secret") + "@ss.example.com:8388?plugin=v2ray-plugin%3Bobfs%3Dwebsocket%3Bobfs-host%3Dcdn.example.com%3Bpath%3D%2Fws%3Btls#SS", want: map[string]any{"name": "SS", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"obfs": "websocket", "obfs-host": "cdn.example.com", "path": "/ws", "tls": true}}},
		// parser-and-template-regressions.test.ts:413.
		{name: "path slash before query", input: "ss://" + b64("aes-128-gcm:secret") + "@ss.example.com:8388/?plugin=" + url.QueryEscape("obfs-local;obfs=tls;obfs-host=cdn.example.com;flag;empty=") + "&uot&tfo=off#SS", want: map[string]any{"name": "SS", "type": "ss", "server": "ss.example.com", "port": 8388, "plugin": "obfs", "plugin-opts": map[string]any{"mode": "tls", "host": "cdn.example.com"}, "udp-over-tcp": true}, absent: []string{"tfo"}},
		// parser-and-template-regressions.test.ts:430.
		{name: "v2ray JSON IPv6", input: "ss://" + b64("aes-128-gcm:secret@[2001:db8::2]:8388") + "?v2ray-plugin=" + url.QueryEscape(b64(`{"mode":"websocket","tls":"0","mux":"1"}`)) + "#V2Ray", want: map[string]any{"name": "V2Ray", "server": "2001:db8::2", "plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket", "tls": false, "mux": true}}},
		// 上游 parseSS 只在插件名为空时读取 v2ray-plugin，且 JSON 必须是对象。
		{name: "empty plugin name falls back", input: "ss://" + user + "@h.example.com:8388?plugin=" + url.QueryEscape(";mode=websocket") + "&v2ray-plugin=" + url.QueryEscape(b64(`{"mode":"websocket"}`)), want: map[string]any{"plugin": "v2ray-plugin", "plugin-opts": map[string]any{"mode": "websocket"}}},
		{name: "JSON null", input: "ss://" + user + "@h.example.com:8388?v2ray-plugin=" + url.QueryEscape(b64("null")), absent: []string{"plugin", "plugin-opts"}},
		{name: "JSON array", input: "ss://" + user + "@h.example.com:8388?v2ray-plugin=" + url.QueryEscape(b64("[1]")), absent: []string{"plugin", "plugin-opts"}},
	})
}

func TestSubBoostSSPluginNormalization(t *testing.T) {
	tests := []struct {
		name   string
		plugin string
		input  map[string]any
		want   string
		opts   map[string]any
	}{
		// ss.test.ts:84.
		{"empty", "", nil, "", nil},
		// ss.test.ts:88.
		{"obfs", "obfs", nil, "obfs", nil},
		// ss.test.ts:92.
		{"v2ray without options", "v2ray-plugin", nil, "v2ray-plugin", nil},
		// ss.test.ts:96.
		{"obfs-local", "obfs-local", map[string]any{"obfs": "http", "obfs-host": "cdn.example.com"}, "obfs", map[string]any{"mode": "http", "host": "cdn.example.com"}},
		// ss.test.ts:100.
		{"gost", "gost-plugin", map[string]any{"tls": "yes", "mux": 0, "extra": "keep"}, "gost-plugin", map[string]any{"tls": true, "mux": false, "extra": "keep"}},
		// ss.test.ts:104.
		{"gost bools", "gost-plugin", map[string]any{"tls": true, "mux": 1}, "gost-plugin", map[string]any{"tls": true, "mux": true}},
		// ss.test.ts:108.
		{"gost non-bools", "gost-plugin", map[string]any{"tls": "", "mux": 2}, "gost-plugin", map[string]any{"tls": "", "mux": 2}},
		// ss.test.ts:112.
		{"xray partial", "xray-plugin", map[string]any{"tls": "off", "mux": "maybe"}, "xray-plugin", map[string]any{"tls": false, "mux": "maybe"}},
		// ss.test.ts:116.
		{"xray on", "xray-plugin", map[string]any{"tls": false, "mux": "on"}, "xray-plugin", map[string]any{"tls": false, "mux": true}},
		// ss.test.ts:120.
		{"xray objects", "xray-plugin", map[string]any{"tls": map[string]any{}, "mux": nil}, "xray-plugin", map[string]any{"tls": map[string]any{}, "mux": nil}},
		// ss.test.ts:124.
		{"simple empty", "simple-obfs", map[string]any{"mode": "", "host": ""}, "obfs", nil},
		// ss.test.ts:128.
		{"custom", "custom-plugin", map[string]any{"mode": "raw"}, "custom-plugin", map[string]any{"mode": "raw"}},
		// parser-edge-cases.test.ts:260.
		{"v2ray numbers", "v2ray-plugin", map[string]any{"mux": 0, "tls": 1, "mode": "websocket"}, "v2ray-plugin", map[string]any{"mux": false, "tls": true, "mode": "websocket"}},
		// parser-edge-cases.test.ts:264.
		{"gost without options", "gost-plugin", nil, "gost-plugin", nil},
		// parser-edge-cases.test.ts:265.
		{"obfs underscore host", "obfs-local", map[string]any{"obfs_host": " front.example.com "}, "obfs", map[string]any{"host": "front.example.com"}},
		// parser-matrix.test.ts:479.
		{"custom flag", "custom-plugin", map[string]any{"raw": true}, "custom-plugin", map[string]any{"raw": true}},
		// parser-matrix.test.ts:487.
		{"gost untouched", "gost-plugin", map[string]any{"tls": 0, "mux": "yes", "untouched": 2}, "gost-plugin", map[string]any{"tls": false, "mux": true, "untouched": 2}},
		// parser-matrix.test.ts:491.
		{"simple-obfs", "simple-obfs", map[string]any{"obfs": "http", "obfs-host": "front.example.com"}, "obfs", map[string]any{"mode": "http", "host": "front.example.com"}},
		// 上游 mode 依次取 mode、obfs，host 依次取 host、obfs-host、obfs_host，只接受字符串。
		{"obfs key priority", "obfs-local", map[string]any{"mode": "tls", "obfs": "http", "host": "a.example.com", "obfs-host": "b.example.com"}, "obfs", map[string]any{"mode": "tls", "host": "a.example.com"}},
		{"obfs flag is not mode", "obfs", map[string]any{"obfs": true, "obfs-host": "x.example.com"}, "obfs", map[string]any{"host": "x.example.com"}},
		// 上游只把 obfs、v2ray、xray、gost 插件名转小写，其他插件名保持原样。
		{"other name keeps case", "Shadow-TLS", map[string]any{"host": "x"}, "Shadow-TLS", map[string]any{"host": "x"}},
		{"known name lowercased", "V2ray-Plugin", nil, "v2ray-plugin", nil},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, opts := normalizeSSPlugin(tt.plugin, tt.input)
			if got != tt.want || !reflect.DeepEqual(opts, tt.opts) {
				t.Fatalf("normalizeSSPlugin = (%q, %#v), want (%q, %#v)", got, opts, tt.want, tt.opts)
			}
		})
	}
}
