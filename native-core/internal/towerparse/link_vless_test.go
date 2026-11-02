package towerparse

import (
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

// Source: protocols/vless.test.ts:10, protocols/vless.test.ts:292, protocols/vless.test.ts:305, protocols/vless.test.ts:318.
func TestParseVLESSRealityAndTransports(t *testing.T) {
	value := parseTestLink(t, "vless://11111111-1111-4111-8111-111111111111@reality.example:443?security=reality&pbk=public-key&sid=0x7250&spx=%2Fspider&fp=chrome&sni=edge.example&type=ws&host=cdn.example&path=%2Fws%3Fed%3D128#Reality")
	if value.Type != "vless" || value.Server != "reality.example" || value.Port != 443 || value.Name != "Reality" {
		t.Fatalf("unexpected VLESS identity: %+v", value)
	}
	if value.Fields["tls"] != true || value.Fields["client-fingerprint"] != "chrome" {
		t.Fatalf("unexpected VLESS TLS fields: %+v", value.Fields)
	}
	reality, ok := value.Fields["reality-opts"].(map[string]any)
	if !ok || reality["public-key"] != "public-key" || reality["short-id"] != "7250" || reality["_spider-x"] != "/spider" {
		t.Fatalf("unexpected Reality fields: %#v", value.Fields["reality-opts"])
	}
	ws, ok := value.Fields["ws-opts"].(map[string]any)
	if !ok || ws["path"] != "/ws" || ws["max-early-data"] != 128 {
		t.Fatalf("unexpected VLESS WS fields: %#v", value.Fields["ws-opts"])
	}

	for _, network := range []string{"http", "h2", "grpc", "xhttp"} {
		link := "vless://uuid@example:443?type=" + network + "&security=tls&host=cdn.example&path=%2Fsvc"
		if network == "grpc" {
			link += "&serviceName=svc"
		}
		parsed := parseTestLink(t, link)
		if parsed.Fields["network"] != network {
			t.Fatalf("network=%s fields=%+v", network, parsed.Fields)
		}
	}
}

// Source: protocols/vless.test.ts:521.
func TestParseVLESSRejectsBadTransportWithoutStopping(t *testing.T) {
	stats, err := Parse(strings.NewReader("vless://uuid@example:443?type=kcp\nvless://uuid@example:443"), func(value flight.Flight) error { return nil })
	if err != nil || stats.Parsed != 1 || stats.Skipped != 1 || stats.SkippedByReason["invalid vless link"] != 1 {
		t.Fatalf("unexpected VLESS failure stats: err=%v stats=%+v", err, stats)
	}
}
