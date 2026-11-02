package towerparse

import (
	"encoding/base64"
	"testing"
)

// Source: protocols/ssr-snell.test.ts:11, protocols/ssr-snell.test.ts:31.
func TestParseSSRStandardBase64(t *testing.T) {
	encode := func(value string) string { return base64.RawURLEncoding.EncodeToString([]byte(value)) }
	payload := encode("[2001:db8::1]:8388:auth_sha1_v4:aes-256-cfb:tls1.2_ticket_auth:" + encode("secret") + "/?remarks=" + encode("SSR Node") + "&protoparam=" + encode("proto") + "&obfsparam=" + encode("host"))
	value := parseTestLink(t, "ssr://"+payload)
	if value.Name != "SSR Node" || value.Type != "ssr" || value.Server != "2001:db8::1" || value.Port != 8388 {
		t.Fatalf("unexpected SSR identity: %+v", value)
	}
	if value.Fields["password"] != "secret" || value.Fields["protocol-param"] != "proto" || value.Fields["obfs-param"] != "host" {
		t.Fatalf("unexpected SSR fields: %+v", value.Fields)
	}
}
