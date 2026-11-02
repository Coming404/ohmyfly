package mobile

import (
	"encoding/base64"
	"encoding/json"
	"testing"
)

type parsedResponse struct {
	Nodes   []parsedNode `json:"nodes"`
	Skipped int          `json:"skipped"`
}

type parsedNode struct {
	Type   string `json:"type"`
	Server string `json:"server"`
	Port   int    `json:"port"`
}

func TestParseSubscription(t *testing.T) {
	const yaml = `proxies:
  - name: alpha
    type: vless
    server: alpha.example.com
    port: 443
    uuid: 00000000-0000-0000-0000-000000000001
  - name: beta
    type: ss
    server: beta.example.com
    port: 8388
    cipher: aes-128-gcm
    password: secret
`
	const link = "vless://00000000-0000-0000-0000-000000000001@link.example.com:8443#Link"
	encoded := base64.StdEncoding.EncodeToString([]byte(link))

	tests := []struct {
		name        string
		content     string
		wantNodes   int
		wantSkipped int
		want        []parsedNode
	}{
		{
			name:      "clash yaml",
			content:   yaml,
			wantNodes: 2,
			want: []parsedNode{
				{Type: "vless", Server: "alpha.example.com", Port: 443},
				{Type: "ss", Server: "beta.example.com", Port: 8388},
			},
		},
		{
			name:      "share link",
			content:   link,
			wantNodes: 1,
			want:      []parsedNode{{Type: "vless", Server: "link.example.com", Port: 8443}},
		},
		{
			name:      "base64 subscription",
			content:   encoded,
			wantNodes: 1,
			want:      []parsedNode{{Type: "vless", Server: "link.example.com", Port: 8443}},
		},
		{
			name:        "empty input",
			content:     "",
			wantNodes:   0,
			wantSkipped: 1,
		},
		{
			name:        "unrecognized input",
			content:     "not a subscription",
			wantNodes:   0,
			wantSkipped: 1,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			encodedResult, err := parseSubscription(test.content)
			if err != nil {
				t.Fatalf("parseSubscription() error = %v", err)
			}
			var got parsedResponse
			if err := json.Unmarshal([]byte(encodedResult), &got); err != nil {
				t.Fatalf("json.Unmarshal() error = %v", err)
			}
			if len(got.Nodes) != test.wantNodes {
				t.Fatalf("node count = %d, want %d", len(got.Nodes), test.wantNodes)
			}
			if got.Skipped != test.wantSkipped {
				t.Fatalf("skipped = %d, want %d", got.Skipped, test.wantSkipped)
			}
			for index, want := range test.want {
				if got.Nodes[index] != want {
					t.Errorf("node %d = %+v, want %+v", index, got.Nodes[index], want)
				}
			}
		})
	}
}
