// 验证整数凭据在字段、锚点和备用扫描路径中的语义。
package towerparse

import (
	"reflect"
	"testing"
)

func TestYAMLCredentialIntegers(t *testing.T) {
	cases := []struct{ name, source, want string }{
		{"leading_zero", "000123", "000123"},
		{"large", "9007199254740993", "9007199254740993"},
		{"huge", "99999999999999999999", "99999999999999999999"},
		{"positive", "+000123", "+000123"}, {"negative", "-000123", "-000123"},
		{"negative_zero", "-0", "-0"}, {"hex", "0xff", "255"},
		{"negative_hex", "-0xff", "-255"}, {"octal", "+0o17", "15"},
		{"binary", "0b101", "5"}, {"underscores", "9_007_199_254_740_993", "9_007_199_254_740_993"},
		{"explicit", "!!int 000123", "000123"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			for _, field := range []string{"password", "username", "psk", "auth-str", "token", "private-key-passphrase", "obfs-password"} {
				root, err := decodeSingleYAML(field + ": " + tc.source + "\nport: 0443")
				if err != nil {
					t.Fatal(err)
				}
				got := root.(map[string]any)
				if got[field] != tc.want || got["port"] != 443 {
					t.Fatalf("%s = %#v, port = %#v", field, got[field], got["port"])
				}
			}
		})
	}
}

func TestYAMLCredentialAliases(t *testing.T) {
	for _, input := range []string{
		"password: &v 000123\nport: *v\nnested: {token: *v}\nlist: [*v]",
		"port: &v 000123\npassword: *v\nnested: {token: *v}\nlist: [*v]",
		"<<: &defaults {password: &v 000123, port: *v}\nnested: {token: *v}\nlist: [*v]",
	} {
		got, err := decodeSingleYAML(input)
		if err != nil {
			t.Fatal(err)
		}
		want := map[string]any{"password": "000123", "port": 123,
			"nested": map[string]any{"token": "000123"}, "list": []any{123}}
		if !reflect.DeepEqual(got, want) {
			t.Fatalf("got %#v, want %#v", got, want)
		}
	}
}

func TestYAMLCredentialScannerParity(t *testing.T) {
	input := "proxies:\n- {name: a, type: ss, server: a.example, port: 443, password: 000123}\n"
	whole, _ := parseYAMLTest(t, input)
	// 第二份文档迫使整份加载失败，保留的扫描器恢复首文档。
	scanned, _ := parseYAMLTest(t, input+"---\nproxies: []\n")
	if len(whole) != 1 || !reflect.DeepEqual(whole, scanned) || whole[0].Fields["password"] != "000123" {
		t.Fatalf("normal=%#v scanned=%#v", whole, scanned)
	}
}

// 日期和二进制是对象类型，不能被 YAML 解码器变成有效字符串密码。
func TestYAMLCredentialObjectTags(t *testing.T) {
	for _, value := range []string{"2026-01-01", "!!timestamp 2026-01-01", "!!binary QQ=="} {
		t.Run(value, func(t *testing.T) {
			input := "proxies:\n- {type: ss, server: a.example, port: 443, password: " + value + "}\n"
			nodes, stats := parseYAMLTest(t, input)
			if len(nodes) != 0 || stats.Skipped != 1 {
				t.Fatalf("nodes=%d skipped=%d", len(nodes), stats.Skipped)
			}
		})
	}
}
