// SSH 加载前检查：mihomo 无法加载或无法认证的 SSH 节点在规范化时被拒绝。
package flight

import "testing"

func TestNormalizeSSHRequiredFields(t *testing.T) {
	tests := []struct {
		name    string
		fields  map[string]any
		wantErr string
	}{
		{name: "password", fields: map[string]any{"username": "root", "password": "p"}},
		{name: "private key", fields: map[string]any{"username": "root", "private-key": "-----BEGIN OPENSSH PRIVATE KEY-----"}},
		{name: "missing username", fields: map[string]any{"password": "p"}, wantErr: "missing ssh username"},
		{name: "blank username", fields: map[string]any{"username": " ", "password": "p"}, wantErr: "missing ssh username"},
		{name: "no auth", fields: map[string]any{"username": "root"}, wantErr: "missing ssh password or private-key"},
		{name: "blank auth", fields: map[string]any{"username": "root", "password": "", "private-key": " "}, wantErr: "missing ssh password or private-key"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			node := map[string]any{"type": "ssh", "server": "ssh.example.com", "port": 22}
			for key, value := range tc.fields {
				node[key] = value
			}
			_, err := Normalize(node)
			if tc.wantErr == "" {
				if err != nil {
					t.Fatalf("Normalize() error = %v", err)
				}
				return
			}
			if err == nil || err.Error() != tc.wantErr {
				t.Fatalf("Normalize() error = %v, want %q", err, tc.wantErr)
			}
		})
	}
}
