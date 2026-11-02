package towerparse

import (
	"encoding/base64"
	"encoding/json"
	"testing"
)

// Source: protocols/vmess.test.ts:9, protocols/vmess.test.ts:83, protocols/vmess.test.ts:105.
func TestParseVMessJSONAndVMess1(t *testing.T) {
	payload, err := json.Marshal(map[string]any{
		"v": "2", "ps": "JSON", "add": "vmess.example", "port": "443",
		"id": "11111111-1111-4111-8111-111111111111", "aid": "0", "scy": "auto",
		"net": "ws", "tls": "tls", "host": "cdn.example", "path": "/ws?ed=64",
	})
	if err != nil {
		t.Fatal(err)
	}
	link := "vmess://" + base64.RawStdEncoding.EncodeToString(payload) + "#Outer"
	value := parseTestLink(t, link)
	if value.Name != "Outer" || value.Type != "vmess" || value.Server != "vmess.example" || value.Port != 443 {
		t.Fatalf("unexpected VMess identity: %+v", value)
	}
	if value.Fields["tls"] != true || value.Fields["uuid"] != "11111111-1111-4111-8111-111111111111" {
		t.Fatalf("unexpected VMess fields: %+v", value.Fields)
	}
	ws, ok := value.Fields["ws-opts"].(map[string]any)
	if !ok || ws["path"] != "/ws" || ws["max-early-data"] != 64 {
		t.Fatalf("unexpected VMess WS fields: %#v", value.Fields["ws-opts"])
	}
	if !value.FromBase64 {
		t.Fatal("VMess JSON link should record base64 source")
	}

	kitsunebi := parseTestLink(t, "vmess1://11111111-1111-4111-8111-111111111111@kit.example:443/app?network=grpc&serviceName=svc#Kit")
	if kitsunebi.Name != "Kit" || kitsunebi.Fields["network"] != "grpc" {
		t.Fatalf("unexpected vmess1 fields: %+v", kitsunebi.Fields)
	}
}

// Source: protocols/vmess.test.ts:83, protocols/vmess.test.ts:105.
func TestParseVMessTransportVariants(t *testing.T) {
	for _, network := range []string{"tcp", "ws", "h2", "grpc", "http"} {
		link := "vmess://uuid@example:443?type=" + network + "&security=tls&host=cdn.example&path=%2Fsvc"
		value := parseTestLink(t, link)
		if value.Fields["network"] != network {
			t.Fatalf("network=%s fields=%+v", network, value.Fields)
		}
	}
}
