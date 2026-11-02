package flight

import (
	"math"
	"testing"
)

func TestFingerprintNonFiniteValuesUseJSONNull(t *testing.T) {
	nullValue, err := Normalize(map[string]any{
		"type": "vless", "server": "node.example.com", "port": 443,
		"plugin-opts": map[string]any{"value": nil},
	})
	if err != nil {
		t.Fatalf("Normalize() null value: %v", err)
	}
	nonFiniteValue, err := Normalize(map[string]any{
		"type": "vless", "server": "node.example.com", "port": 443,
		"plugin-opts": map[string]any{"value": math.NaN()},
	})
	if err != nil {
		t.Fatalf("Normalize() non-finite value: %v", err)
	}
	if Fingerprint(nullValue) != Fingerprint(nonFiniteValue) {
		t.Fatal("non-finite fingerprint does not match its JSON null representation")
	}
}
