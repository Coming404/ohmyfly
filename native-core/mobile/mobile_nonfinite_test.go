package mobile

import (
	"encoding/json"
	"testing"
)

func TestParseSubscriptionUsesJSONNullForNonFiniteValues(t *testing.T) {
	const content = `proxies:
  - name: edge
    type: vless
    server: edge.example.com
    port: 443
    uuid: 00000000-0000-0000-0000-000000000001
    plugin-opts:
      nan: .nan
      plus: .inf
      minus: -.inf
`
	encoded, err := ParseSubscription(content)
	if err != nil {
		t.Fatalf("ParseSubscription() error = %v", err)
	}
	var result struct {
		Nodes []struct {
			Fields map[string]any `json:"fields"`
		} `json:"nodes"`
	}
	if err := json.Unmarshal([]byte(encoded), &result); err != nil {
		t.Fatalf("json.Unmarshal() error = %v", err)
	}
	options := result.Nodes[0].Fields["plugin-opts"].(map[string]any)
	for _, key := range []string{"nan", "plus", "minus"} {
		if value := options[key]; value != nil {
			t.Errorf("plugin-opts[%q] = %#v, want JSON null", key, value)
		}
	}
}
