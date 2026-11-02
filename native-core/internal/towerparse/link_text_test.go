package towerparse

import (
	"encoding/base64"
	"testing"
)

// Expected values follow JS String.prototype.trim, /\s/ and the WHATWG
// UTF-8 decoder used by TextDecoder and URLSearchParams.
func TestJSTrimSpace(t *testing.T) {
	cases := map[string]string{
		"\uFEFF\u00A0 vless://a \u3000\u2028": "vless://a",
		"\u0085vless://a\u0085":               "\u0085vless://a\u0085",
	}
	for input, want := range cases {
		if got := jsTrimSpace(input); got != want {
			t.Errorf("jsTrimSpace(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestDecodeUTF8Lossy(t *testing.T) {
	cases := []struct {
		input []byte
		want  string
	}{
		{[]byte{0xE4, 0xB8}, "\uFFFD"},
		{[]byte{'a', 0xE4, 0xB8, 'b'}, "a\uFFFDb"},
		{[]byte{0xF0, 0x9F, 0x98}, "\uFFFD"},
		{[]byte{0xC0, 0x80}, "\uFFFD\uFFFD"},
		{[]byte{0xED, 0xA0, 0x80}, "\uFFFD\uFFFD\uFFFD"},
		{[]byte{0x80}, "\uFFFD"},
		{[]byte{0xE4, 0xB8, 0x41}, "\uFFFDA"},
		{[]byte{0xEF, 0xBF, 0xBD, 0xFF}, "\uFFFD\uFFFD"},
	}
	for _, tc := range cases {
		if got := decodeUTF8Lossy(tc.input); got != tc.want {
			t.Errorf("decodeUTF8Lossy(% X) = %q, want %q", tc.input, got, tc.want)
		}
	}
}

func TestLinkSearchParamsTruncatedUTF8(t *testing.T) {
	if got := linkSearchParams("sni=%E4%B8&x=1").Get("sni"); got != "\uFFFD" {
		t.Fatalf("sni = %q, want one U+FFFD", got)
	}
}

func TestDecodeLinkBase64JSWhitespace(t *testing.T) {
	if got, err := decodeLinkText("\uFEFFYW\u3000I\u00A0="); err != nil || got != "ab" {
		t.Fatalf("decodeLinkText = %q, %v, want \"ab\"", got, err)
	}
	if _, err := decodeLinkText("YW\u0085I="); err == nil {
		t.Fatal("U+0085 is not JS whitespace and must not be removed")
	}
	encoded := base64.StdEncoding.EncodeToString([]byte{'o', 0xE4, 0xB8})
	if got, err := decodeLinkText(encoded); err != nil || got != "o\uFFFD" {
		t.Fatalf("decodeLinkText(invalid UTF-8) = %q, %v", got, err)
	}
}
