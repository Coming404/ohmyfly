package towerparse

import (
	"encoding/base64"
	"net/url"
	"testing"
)

// Source: protocols/ss.test.ts:9, protocols/ss.test.ts:55, protocols/ss.test.ts:135.
func TestParseSSSIP002AndLegacy(t *testing.T) {
	legacy := "ss://" + base64.RawStdEncoding.EncodeToString([]byte("aes-128-gcm:secret@ss.example:8388")) + "#Legacy"
	value := parseTestLink(t, legacy)
	if value.Name != "Legacy" || value.Type != "ss" || value.Server != "ss.example" || value.Port != 8388 || value.Fields["password"] != "secret" {
		t.Fatalf("unexpected legacy SS: %+v", value)
	}
	plugin := url.QueryEscape("obfs-local;obfs=tls;obfs-host=cdn.example")
	sip := parseTestLink(t, "ss://aes-256-gcm:pass@[2001:db8::1]:8388?plugin="+plugin+"&uot=1&tfo=yes#SIP002")
	if sip.Server != "2001:db8::1" || sip.Fields["plugin"] != "obfs" || sip.Fields["udp-over-tcp"] != true || sip.Fields["tfo"] != true {
		t.Fatalf("unexpected SIP002 SS: %+v", sip.Fields)
	}
	options, ok := sip.Fields["plugin-opts"].(map[string]any)
	if !ok || options["mode"] != "tls" || options["host"] != "cdn.example" {
		t.Fatalf("unexpected SS plugin options: %#v", sip.Fields["plugin-opts"])
	}
}
