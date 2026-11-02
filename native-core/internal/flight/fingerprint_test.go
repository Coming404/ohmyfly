package flight

import "testing"

func normalizedForTest(t *testing.T, fields map[string]any) Flight {
	t.Helper()
	value, err := Normalize(fields)
	if err != nil {
		t.Fatalf("normalize failed: %v", err)
	}
	return value
}

func TestFingerprintIgnoresNamesAndInternalFields(t *testing.T) {
	base := map[string]any{
		"name":   "one",
		"type":   "vless",
		"server": "Example.COM.",
		"port":   "443",
		"uuid":   "UUID-case-sensitive",
		"_meta":  map[string]any{"source": "first"},
		"extra":  map[string]any{"_source": "first", "enabled": true},
	}
	other := map[string]any{
		"name":   "two",
		"type":   "vless",
		"server": "example.com",
		"port":   443,
		"uuid":   "UUID-case-sensitive",
		"_meta":  map[string]any{"source": "second"},
		"extra":  map[string]any{"_source": "second", "enabled": true},
	}
	if Fingerprint(normalizedForTest(t, base)) != Fingerprint(normalizedForTest(t, other)) {
		t.Fatal("names or internal fields changed the content fingerprint")
	}
}

func TestFingerprintAliasesAndPortForms(t *testing.T) {
	canonical := map[string]any{
		"name": "canonical", "type": "vless", "server": "node.example", "port": 443,
		"uuid": "uuid", "client-fingerprint": "chrome",
		"reality-opts": map[string]any{"public-key": "public", "short-id": "0725"},
	}
	aliases := map[string]any{
		"name": "aliases", "type": "vless", "server": "NODE.EXAMPLE.", "port": "443",
		"uuid": "uuid", "fp": "chrome",
		"reality-opts": map[string]any{"pbk": "public", "sid": "0x725"},
	}
	if Fingerprint(normalizedForTest(t, canonical)) != Fingerprint(normalizedForTest(t, aliases)) {
		t.Fatal("equivalent aliases or port forms produced different fingerprints")
	}
}

func TestFingerprintKeepsCredentialsAndTransportQueries(t *testing.T) {
	base := map[string]any{
		"type": "ss", "server": "node.example", "port": 8388,
		"cipher": "chacha20-ietf-poly1305", "password": "CaseSensitive",
		"network": "ws", "ws-opts": map[string]any{"path": "/entry?token=one&ed=128"},
	}
	password := map[string]any{
		"type": "ss", "server": "node.example", "port": 8388,
		"cipher": "chacha20-ietf-poly1305", "password": "casesensitive",
		"network": "ws", "ws-opts": map[string]any{"path": "/entry?token=one&ed=128"},
	}
	query := map[string]any{
		"type": "ss", "server": "node.example", "port": 8388,
		"cipher": "chacha20-ietf-poly1305", "password": "CaseSensitive",
		"network": "ws", "ws-opts": map[string]any{"path": "/entry?token=two&ed=128"},
	}
	if Fingerprint(normalizedForTest(t, base)) == Fingerprint(normalizedForTest(t, password)) {
		t.Fatal("credential case was incorrectly folded")
	}
	if Fingerprint(normalizedForTest(t, base)) == Fingerprint(normalizedForTest(t, query)) {
		t.Fatal("non-ed WebSocket query was discarded")
	}
}

func TestFingerprintIncludesCipherAndUnknownFields(t *testing.T) {
	base := map[string]any{"type": "ss", "server": "n.example", "port": 1, "cipher": "aes", "password": "p", "future": "one"}
	cipher := map[string]any{"type": "ss", "server": "n.example", "port": 1, "cipher": "chacha", "password": "p", "future": "one"}
	unknown := map[string]any{"type": "ss", "server": "n.example", "port": 1, "cipher": "aes", "password": "p", "future": "two"}
	if Fingerprint(normalizedForTest(t, base)) == Fingerprint(normalizedForTest(t, cipher)) {
		t.Fatal("cipher was omitted from fingerprint")
	}
	if Fingerprint(normalizedForTest(t, base)) == Fingerprint(normalizedForTest(t, unknown)) {
		t.Fatal("unknown field was omitted from fingerprint")
	}
}

func TestFingerprintKeepsNestedName(t *testing.T) {
	a := map[string]any{"type": "vless", "server": "n.example", "port": 1, "uuid": "u", "plugin-opts": map[string]any{"name": "a"}}
	b := map[string]any{"type": "vless", "server": "n.example", "port": 1, "uuid": "u", "plugin-opts": map[string]any{"name": "b"}}
	if Fingerprint(normalizedForTest(t, a)) == Fingerprint(normalizedForTest(t, b)) {
		t.Fatal("nested name must stay in the fingerprint")
	}
}
