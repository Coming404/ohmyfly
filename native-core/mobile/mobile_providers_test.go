// 验证 provider JSON 契约和跨来源节点的配置身份。
package mobile

import (
	"encoding/json"
	"testing"
)

func TestParseSubscriptionProviders(t *testing.T) {
	input := "proxy-providers:\n  remote: {type: http, url: https://provider.example/list, header: {User-Agent: [test]}, health-check: {enable: true}, extra: .inf}\n"
	var result subscriptionResult
	if err := json.Unmarshal([]byte(ParseSubscription(input)), &result); err != nil {
		t.Fatal(err)
	}
	if len(result.Nodes) != 0 || len(result.ProxyProviders) != 1 {
		t.Fatal("unexpected nodes or provider count")
	}
	provider := result.ProxyProviders[0]
	if provider.Name != "remote" || provider.URL != "https://provider.example/list" {
		t.Fatal("provider identity lost")
	}
	if provider.Fields["extra"] != nil {
		t.Fatal("non-finite field must be null")
	}
	if provider.Fields["header"].(map[string]any)["User-Agent"].([]any)[0] != "test" {
		t.Fatal("request header lost")
	}
	if result.Parsed != 0 {
		t.Fatal("provider was counted as a parsed node")
	}
}

func TestParseSubscriptionFingerprintContract(t *testing.T) {
	inputs := []string{
		"proxies:\n- {name: a, type: ss, server: a.example, port: 443, cipher: aes-128-gcm, password: 000123}",
		"proxies:\n- {name: b, type: ss, server: a.example, port: 443, cipher: aes-128-gcm, password: '000123'}",
		"proxies:\n- {name: c, type: ss, server: a.example, port: 443, cipher: aes-128-gcm, password: '123'}",
	}
	var fingerprints []string
	for _, input := range inputs {
		var result subscriptionResult
		if err := json.Unmarshal([]byte(ParseSubscription(input)), &result); err != nil {
			t.Fatal(err)
		}
		if len(result.Nodes) != 1 || len(result.Nodes[0].Fingerprint) != 64 {
			t.Fatal("missing node identity")
		}
		if result.ProxyProviders == nil {
			t.Fatal("provider list must be [] rather than null")
		}
		fingerprints = append(fingerprints, result.Nodes[0].Fingerprint)
	}
	if fingerprints[0] != fingerprints[1] {
		t.Fatal("name changed configuration identity")
	}
	if fingerprints[0] == fingerprints[2] {
		t.Fatal("leading zero in credential was lost")
	}
}
