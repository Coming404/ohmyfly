// 验证根语法识别和 Base64 包装不会把 YAML 节点送入链接解析器。
package towerparse

import (
	"encoding/base64"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func TestDetectYAMLRootSyntax(t *testing.T) {
	cases := []struct {
		name, input string
		want        Format
	}{
		{"json", `{"proxies":[]}`, FormatYAML},
		{"single", "type: ss\nserver: a.example\nport: 443", FormatYAML},
		{"flow_item", "- {type: ss, server: a.example, port: 443}", FormatYAML},
		{"flow_array", "[{type: ss, server: a.example, port: 443}]", FormatYAML},
		{"explicit_key", "? proxies\n: []", FormatYAML},
		{"anchor", "&root {proxies: []}", FormatYAML},
		{"tag", "!!map {proxies: []}", FormatYAML},
		{"spaced_key", "proxies : []", FormatYAML},
		{"quoted_key", "'proxies' : []", FormatYAML},
		{"marker", "--- # start\n- {type: ss}", FormatYAML},
		{"directive", "%YAML 1.2\n---\nproxies: []", FormatYAML},
		{"cr", "# comment\rproxies: []", FormatYAML},
		{"link", "---\nss://abc@a.example:443", FormatLink},
		{"uri_colon", "vmess://abc", FormatLink},
		{"root_priority", "ss://abc\nproxies: []", FormatYAML},
		{"flow_strings", "[Proxy]", FormatYAML},
		{"platform", "[Proxy]\na = ss, a.example, 443", FormatUnknown},
		{"plain", "plain text!", FormatUnknown},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := DetectFormat([]byte(tc.input)); got != tc.want {
				t.Fatalf("got %s, want %s", got, tc.want)
			}
		})
	}
}

func TestParseYAMLRootForms(t *testing.T) {
	for _, input := range []string{
		`{"proxies":[{"name":"a","type":"ss","server":"a.example","port":443,"password":"p"}]}`,
		"name: a\ntype: ss\nserver: a.example\nport: 443\npassword: p",
		"- {name: a, type: ss, server: a.example, port: 443, password: p}",
	} {
		for _, content := range []string{input, base64.StdEncoding.EncodeToString([]byte(input))} {
			var nodes []flight.Flight
			stats, err := Parse(strings.NewReader(content), func(node flight.Flight) error {
				nodes = append(nodes, node)
				return nil
			})
			if err != nil {
				t.Fatal(err)
			}
			if len(nodes) != 1 || stats.YAML != 1 || nodes[0].SourceFormat != "yaml" {
				t.Fatalf("nodes=%d stats=%+v", len(nodes), stats)
			}
		}
	}
}
