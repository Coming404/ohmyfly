package towerparse

import (
	"encoding/base64"
	"testing"
)

func TestVLESSUpstreamAuthorityAlignment(t *testing.T) {
	const uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
	noQuery := base64.RawURLEncoding.EncodeToString([]byte(uuid + "@shadow-no-query.example.com:443"))
	shadow := base64.StdEncoding.EncodeToString([]byte("auto:" + uuid + "@sr.example.com:8443"))
	rejects := []string{
		"vless://" + uuid + "@missing-host-port.example.com?x=1",
		"vless://" + uuid + "@empty-host-port.example.com:?x=1",
		"vless://" + uuid + "@bad-host-port.example.com:abc?x=1",
		"vless://" + uuid + "@[2001:db8::1?x=1",
		"vless://" + uuid + "@?x=1",
		"vless://" + noQuery,
		"vless://" + uuid + "@[2001:db8::1]?security=tls",
	}
	for _, link := range rejects {
		if node, err := parseVLESSLink(link); err == nil {
			t.Errorf("%s: want error, got server=%v port=%v", link, node["server"], node["port"])
		}
	}
	accepts := []struct {
		link   string
		server string
		port   int
	}{
		{"vless://" + uuid + "@h.example.com#n", "h.example.com", 443},
		{"vless://" + uuid + "@[2001:db8::1]:8443?security=tls", "2001:db8::1", 8443},
		{"vless://" + uuid + "@h.example.com:443/?type=tcp#n", "h.example.com", 443},
		{"vless://" + shadow + "?tls=1#sr", "sr.example.com", 8443},
	}
	for _, tc := range accepts {
		node, err := parseVLESSLink(tc.link)
		if err != nil {
			t.Errorf("%s: unexpected error %v", tc.link, err)
			continue
		}
		if node["server"] != tc.server || node["port"] != tc.port || node["uuid"] != uuid {
			t.Errorf("%s: got server=%v port=%v uuid=%v", tc.link, node["server"], node["port"], node["uuid"])
		}
	}
}
