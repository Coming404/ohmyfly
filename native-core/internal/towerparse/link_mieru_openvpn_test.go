// Mieru 与 OpenVPN 内部链接规范化回归。
package towerparse

import (
	"net/url"
	"reflect"
	"testing"
)

// 验证 Mieru 内部链接保留 Mihomo 节点字段。
func TestMieruInternalLink(t *testing.T) {
	got := parseTestLink(t, "mieru://alice:secret@edge.example:443?transport=TCP&multiplexing=MULTIPLEXING_LOW&trafficPattern=burst#Edge")
	if got.Type != "mieru" || got.Name != "Edge" || got.Port != 443 || got.Fields["username"] != "alice" || got.Fields["password"] != "secret" || got.Fields["transport"] != "TCP" {
		t.Fatalf("Mieru fields mismatch: %+v", got)
	}
	if _, exists := got.Fields["port-range"]; exists {
		t.Fatal("port link unexpectedly has port-range")
	}
}

// 验证 Mieru port-range 内部链接字段。
func TestMieruPortRangeInternalLink(t *testing.T) {
	got := parseTestLink(t, "mieru://edge.example?portRange=2090-2099&username=alice#Edge")
	if got.Type != "mieru" || got.Port != 0 || got.Fields["port-range"] != "2090-2099" || got.Fields["username"] != "alice" {
		t.Fatalf("Mieru range fields mismatch: %+v", got)
	}
}

// 验证 OpenVPN 多值、凭据及结构字段解析。
func TestOpenVPNInternalLink(t *testing.T) {
	peerInfo := url.QueryEscape(`{"IV_VER":"2.6"}`)
	ipStack := url.QueryEscape(`{"ipv4":"system"}`)
	got := parseTestLink(t, "openvpn://vpn.example?proto=udp&cipher=AES-256-GCM&data-ciphers=AES-128-GCM&data-ciphers=AES-256-GCM&ca=ca-data&key=private-key&peer-info="+peerInfo+"&ip-stack="+ipStack+"&udp=true#VPN")
	if got.Type != "openvpn" || got.Name != "VPN" || got.Server != "vpn.example" || got.Port != defaultOpenVPNLinkPort {
		t.Fatalf("OpenVPN endpoint mismatch: %+v", got)
	}
	if !reflect.DeepEqual(got.Fields["data-ciphers"], []string{"AES-128-GCM", "AES-256-GCM"}) || got.Fields["udp"] != true || got.Fields["key"] != "private-key" {
		t.Fatalf("OpenVPN fields mismatch: %+v", got.Fields)
	}
	if !reflect.DeepEqual(got.Fields["peer-info"], map[string]any{"IV_VER": "2.6"}) || !reflect.DeepEqual(got.Fields["ip-stack"], map[string]any{"ipv4": "system"}) {
		t.Fatalf("OpenVPN structured fields mismatch: %+v", got.Fields)
	}
}
