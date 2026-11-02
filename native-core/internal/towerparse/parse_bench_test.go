package towerparse

import (
	"encoding/base64"
	"fmt"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

const parseBenchmarkItems = 10000

func benchmarkParseInput(b *testing.B, input string) {
	b.Helper()
	b.ReportAllocs()
	b.SetBytes(int64(len(input)))
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		stats, err := Parse(strings.NewReader(input), func(flight.Flight) error { return nil })
		if err != nil || stats.Parsed != parseBenchmarkItems || stats.Skipped != 0 {
			b.Fatalf("parse: err=%v stats=%+v", err, stats)
		}
	}
}

func BenchmarkParseYAML(b *testing.B) {
	protocols := []string{"ss", "vmess", "vless", "trojan", "hysteria2"}
	var input strings.Builder
	input.WriteString("proxies:\n")
	for i := 0; i < parseBenchmarkItems; i++ {
		protocol := protocols[i%len(protocols)]
		if i%2 == 0 {
			fmt.Fprintf(&input, "  - name: node-%d\n    type: %s\n    server: node-%d.example\n    port: 443\n    uuid: user-%d\n    password: secret\n    cipher: aes-128-gcm\n", i, protocol, i, i)
		} else {
			fmt.Fprintf(&input, "  - {name: node-%d, type: %s, server: node-%d.example, port: 443, uuid: user-%d, password: secret, cipher: aes-128-gcm}\n", i, protocol, i, i)
		}
	}
	benchmarkParseInput(b, input.String())
}

func benchmarkShareLinks() string {
	vmess := "vmess://" + base64.RawStdEncoding.EncodeToString([]byte(`{"v":"2","ps":"vmess","add":"vmess.example","port":"443","id":"11111111-1111-4111-8111-111111111111","aid":"0","net":"tcp","tls":"tls"}`))
	links := []string{
		"ss://aes-128-gcm:secret@ss.example:443#ss",
		vmess,
		"vless://11111111-1111-4111-8111-111111111111@vless.example:443?security=tls#vless",
		"trojan://secret@trojan.example:443#trojan",
		"hysteria2://secret@hy2.example:443#hy2",
	}
	var input strings.Builder
	for i := 0; i < parseBenchmarkItems; i++ {
		input.WriteString(links[i%len(links)])
		input.WriteByte('\n')
	}
	return input.String()
}

func BenchmarkParseLinks(b *testing.B) {
	benchmarkParseInput(b, benchmarkShareLinks())
}

func BenchmarkParseBase64Links(b *testing.B) {
	input := base64.StdEncoding.EncodeToString([]byte(benchmarkShareLinks()))
	benchmarkParseInput(b, input)
}
