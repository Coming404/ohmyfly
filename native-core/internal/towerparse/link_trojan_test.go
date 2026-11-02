package towerparse

import "testing"

// Source: protocols/more-protocols.test.ts:99, protocols/more-protocols.test.ts:102.
func TestParseTrojanTransports(t *testing.T) {
	ws := parseTestLink(t, "trojan://pa%3Ass@trojan.example:443?type=httpupgrade&host=cdn.example&path=%2Fws%3Fed%3D256&allow-insecure=1&fp=chrome&alpn=h2,http%2F1.1#Trojan")
	if ws.Name != "Trojan" || ws.Fields["network"] != "ws" || ws.Fields["skip-cert-verify"] != true {
		t.Fatalf("unexpected Trojan fields: %+v", ws.Fields)
	}
	options, ok := ws.Fields["ws-opts"].(map[string]any)
	if !ok || options["path"] != "/ws" || options["max-early-data"] != 256 || options["v2ray-http-upgrade"] != true {
		t.Fatalf("unexpected Trojan WS fields: %#v", ws.Fields["ws-opts"])
	}
	grpc := parseTestLink(t, "trojan://secret@grpc.example:443?type=grpc&serviceName=svc#GRPC")
	if grpc.Fields["network"] != "grpc" {
		t.Fatalf("unexpected Trojan gRPC fields: %+v", grpc.Fields)
	}
}
