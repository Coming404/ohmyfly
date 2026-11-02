// Ported from SubBoost packages/core/src/parser/protocols/vless.test.ts, vless-reality.test.ts, vmess.test.ts,
// vmess-utils.test.ts, url-decode.test.ts, vless-xhttp.test.ts, ss.test.ts, and ssr-snell.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/base64"
	"reflect"
	"testing"
)

func assertMapSubset(t *testing.T, got, want map[string]any) {
	t.Helper()
	for key, expected := range want {
		actual, exists := got[key]
		if !exists {
			t.Fatalf("missing field %q in %#v", key, got)
		}
		assertValueSubset(t, key, actual, expected)
	}
}

func assertValueSubset(t *testing.T, key string, actual, expected any) {
	t.Helper()
	wantMap, wantOK := expected.(map[string]any)
	gotMap, gotOK := actual.(map[string]any)
	if wantOK {
		if !gotOK {
			t.Fatalf("field %q = %#v, want map %#v", key, actual, expected)
		}
		assertMapSubset(t, gotMap, wantMap)
		return
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("field %q = %#v, want %#v", key, actual, expected)
	}
}

func subBoostBase64(value string) string {
	return base64.StdEncoding.EncodeToString([]byte(value))
}

func subBoostBase64URL(value string) string {
	return base64.RawURLEncoding.EncodeToString([]byte(value))
}
