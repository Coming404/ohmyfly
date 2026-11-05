// Ported from SubBoost packages/core/src/parser/protocols/simple-proxy.ts and simple-proxy.test.ts @4015347 (AGPL-3.0-only).
// Regression cases ported from test/regression/core/parser-{matrix,protocol-matrix,link-edge-cases,protocol-edge-cases,edge-cases,and-template-edge-cases,and-template-regressions}.test.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"net/url"
	"strings"
	"testing"
)

type simpleProxyTestCase struct {
	name, input string
	want        map[string]any
	absent      []string
	message     string
}

func testSimpleProxyLinks(t *testing.T, parse func(string) (map[string]any, error), cases []simpleProxyTestCase) {
	t.Helper()
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			node, err := parse(tc.input)
			if tc.message != "" {
				if err == nil || !strings.Contains(err.Error(), tc.message) {
					t.Fatalf("error = %v, want %q", err, tc.message)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			assertMapSubset(t, node, tc.want)
			assertMapAbsent(t, node, tc.absent...)
		})
	}
}

func TestSocksLinks(t *testing.T) {
	encodedMissingPort := subBoostBase64URL("alice:secret@socks-missing-port.example.com")
	control := subBoostBase64URL("\x00bad")
	userOnly := subBoostBase64URL("only-user")
	testSimpleProxyLinks(t, parseSocksLink, []simpleProxyTestCase{
		{"encoded authority remarks", "socks://" + subBoostBase64URL("alice:secret@socks.example.com:1080") + "?remarks=Encoded", map[string]any{"name": "Encoded", "type": "socks5", "server": "socks.example.com", "port": 1080, "username": "alice", "password": "secret", "udp": true}, []string{"tls"}, ""},
		{"encoded authority without auth", "socks://" + subBoostBase64URL("plain-socks.example.com:1080"), map[string]any{"name": "SOCKS-plain-socks.example.com:1080", "server": "plain-socks.example.com", "port": 1080}, []string{"username", "password"}, ""},
		{"encoded username", "socks5://" + subBoostBase64URL("bob:secret") + "@socks-auth.example.com:1080#Auth", map[string]any{"name": "Auth", "username": "bob", "password": "secret"}, nil, ""},
		{"TLS UDP and SNI", "socks5+tls://carol:secret@socks-tls.example.com:443?udp=0&sni=socks.example.com#TLS", map[string]any{"name": "TLS", "type": "socks5", "tls": true, "udp": false, "sni": "socks.example.com"}, nil, ""},
		{"TLS verification", "socks5://verified-socks.example.com:1080?tls-verification=true#VerifiedSocks", map[string]any{"name": "VerifiedSocks", "skip-cert-verify": false}, nil, ""},
		{"SOCKS4 query aliases", "socks4://dave@socks4.example.com?udp-relay=yes&allow_insecure=true#S4", map[string]any{"name": "S4", "type": "socks4", "port": 1080, "username": "dave", "udp": true, "skip-cert-verify": true}, nil, ""},
		{"default name", "socks5://socks-default.example.com:1080", map[string]any{"name": "SOCKS-socks-default.example.com:1080", "type": "socks5", "port": 1080}, nil, ""},
		{"control authority stays a token", "socks://" + control + "#Control", map[string]any{"name": "Control", "server": control, "port": 1080}, nil, ""},
		{"encoded missing port stays a token", "socks://" + encodedMissingPort + "#MissingPort", map[string]any{"name": "MissingPort", "server": encodedMissingPort, "port": 1080}, []string{"username", "password"}, ""},
		{"encoded username without colon stays raw", "socks5://" + userOnly + "@encoded-user.example.com:1080#RawEncoded", map[string]any{"name": "RawEncoded", "username": userOnly}, []string{"password"}, ""},
		{"regression encoded control username", "socks5://" + subBoostBase64("\x00bad") + "@raw.example.com:1080", map[string]any{"username": subBoostBase64("\x00bad")}, []string{"password"}, ""},
		{"regression decoded username containing at stays raw", "socks5://" + subBoostBase64URL("user@example.com") + "@host.example.com:1080", map[string]any{"username": subBoostBase64URL("user@example.com")}, []string{"password"}, ""},
		{"regression matrix TLS verification and UDP", "socks5+tls://user:pass@socks.example.com:1081?udp=no&tls-verification=false", map[string]any{"tls": true, "udp": false, "skip-cert-verify": true, "port": 1081}, nil, ""},
		{"regression TLS default port", "socks5+tls://" + subBoostBase64URL("sockUser:sockPass") + "@socks.example.com?udp=off&sni=socks.example.com", map[string]any{"name": "SOCKS-socks.example.com:443", "port": 443, "username": "sockUser", "password": "sockPass", "tls": true, "udp": false, "sni": "socks.example.com"}, nil, ""},
		{"regression SOCKS4 defaults", "socks4://user@socks4.example.com", map[string]any{"name": "SOCKS-socks4.example.com:1080", "type": "socks4", "username": "user", "udp": false}, []string{"tls", "password"}, ""},
		{"regression UDP relay and TLS name", "socks://useronly@socks.example.com?udp-relay=yes&tls_name=socks-sni.example.com", map[string]any{"username": "useronly", "udp": true, "sni": "socks-sni.example.com"}, nil, ""},
		{"regression standard base64 encoded auth and UDP", "socks5://" + subBoostBase64("encoded:secret") + "@socks.example.com:1080?udp=off#SocksAuth", map[string]any{"name": "SocksAuth", "username": "encoded", "password": "secret", "udp": false}, nil, ""},
		{"IPv6 name and suffix", "socks5://u:p@[2001:db8::1]{IPv6+Socks}", map[string]any{"name": "IPv6 Socks", "server": "2001:db8::1", "port": 1080}, nil, ""},
		{"IPv6 default name", "socks5://[2001:db8::2]", map[string]any{"name": "SOCKS-2001:db8::2:1080", "server": "2001:db8::2"}, nil, ""},
		{"encoded zero port follows upstream", "socks://" + subBoostBase64URL("h:0"), map[string]any{"server": "h", "port": 0}, nil, ""},
		{"encoded negative port follows upstream", "socks://" + subBoostBase64URL("h:-1"), map[string]any{"port": -1}, nil, ""},
		{"encoded large port follows upstream", "socks://" + subBoostBase64URL("h:70000"), map[string]any{"port": 70000}, nil, ""},
		{"encoded auth is not percent decoded", "socks://" + subBoostBase64URL(" u%20:p%3Ass@h:1080"), map[string]any{"username": "u%20", "password": "p%3Ass"}, nil, ""},
		{"encoded auth splits first colon and last at", "socks://" + subBoostBase64URL("u:p:a@b@h:1080"), map[string]any{"username": "u", "password": "p:a@b", "server": "h"}, nil, ""},
		{"Node padding ignores trailing token text", "socks://" + subBoostBase64URL("u:p@h:1080") + "=ignored", map[string]any{"username": "u", "password": "p", "server": "h"}, nil, ""},
		{"Node ignores incomplete final sextet", "socks://" + subBoostBase64URL("h:1080") + "A", map[string]any{"server": "h", "port": 1080}, nil, ""},
		{"encoded UTF8 credentials", "socks://" + subBoostBase64URL("用户:密钥@h:1080"), map[string]any{"username": "用户", "password": "密钥"}, nil, ""},
		{"encoded UTF8 BOM is removed", "socks://" + subBoostBase64URL("\uFEFFu:p@h:1080"), map[string]any{"username": "u", "password": "p"}, nil, ""},
		{"encoded invalid UTF8 byte is replaced", "socks://" + subBoostBase64URL("u:\xff@h:1080"), map[string]any{"username": "u", "password": "\uFFFD"}, nil, ""},
		{"percent encoded username base64 token", "socks5://dTpw%3D%3D@h", map[string]any{"username": "u", "password": "p"}, nil, ""},
		{"explicit legacy default name is preserved", "socks5://h:1080#SOCKS5-h:1080", map[string]any{"name": "SOCKS5-h:1080"}, nil, ""},
		{"trim and mixed case scheme", " \tSOCKS5://u:p@h:+12#%2520%2B+%E4%B8%AD\n", map[string]any{"name": "%20+ 中", "port": 12, "username": "u", "password": "p"}, nil, ""},
		{"boolean and string alias priorities", "socks5://h?udp=bad&udp-relay=no&skip-cert-verify=bad&allowInsecure=no&sni=+&peer=ignored", map[string]any{"udp": true, "skip-cert-verify": false}, []string{"sni"}, ""},
		{"malformed escapes are preserved", "socks5://u%zz:p%3A%2B%2520@h:443abc#bad+%zz", map[string]any{"username": "u%zz", "password": "p:+%20", "name": "bad+%zz", "port": 443}, nil, ""},
		{"wrong scheme", "http://example.com:1080", nil, nil, "无效的 SOCKS 链接"},
		{"no naked entry point", "socks.example.com:1080", nil, nil, "无效的 SOCKS 链接"},
		{"missing server", "socks://", nil, nil, "缺少服务器地址"},
		{"invalid IPv6", "socks5://[::1]tail", nil, nil, "invalid URL"},
		{"zero standard port", "socks5://h:0", nil, nil, "无效的端口号"},
		{"negative standard port", "socks5://h:-1", nil, nil, "无效的端口号"},
		{"large standard port", "socks5://h:70000", nil, nil, "无效的端口号"},
		{"malformed standard port", "socks5://h:%zz", nil, nil, "无效的端口号"},
	})
}

func TestHTTPProxyLinks(t *testing.T) {
	jsonHeaders := url.QueryEscape(`{"User-Agent":"SubBoost","X-Test":"yes"}`)
	missingPort := subBoostBase64("decoded-without-port")
	control := subBoostBase64("\x00bad")
	testSimpleProxyLinks(t, parseHTTPProxyLink, []simpleProxyTestCase{
		{"HTTP auth headers SNI and verification", "http://user:pass@http.example.com:8080?headers=User-Agent:SubBoost|X-Test:yes&tls-verification=false&sni=edge.example.com#HTTP", map[string]any{"name": "HTTP", "type": "http", "server": "http.example.com", "port": 8080, "tls": false, "username": "user", "password": "pass", "sni": "edge.example.com", "skip-cert-verify": true, "headers": map[string]any{"User-Agent": "SubBoost", "X-Test": "yes"}}, nil, ""},
		{"JSON headers", "http://json.example.com:8080?headers=" + jsonHeaders + "#JSON", map[string]any{"name": "JSON", "headers": map[string]any{"User-Agent": "SubBoost", "X-Test": "yes"}}, nil, ""},
		{"JSON array without header pairs", "http://bad-json.example.com:8080?headers=%5B1%5D#BadJSON", map[string]any{"name": "BadJSON"}, []string{"headers"}, ""},
		{"HTTPS default", "https://secure.example.com#Secure", map[string]any{"name": "Secure", "type": "https", "server": "secure.example.com", "port": 443, "tls": true}, nil, ""},
		{"colon auth", "http://colon.example.com:8080:user:p%40ss#Colon", map[string]any{"name": "Colon", "username": "user", "password": "p@ss", "server": "colon.example.com", "port": 8080}, nil, ""},
		{"header alias keeps empty values", "http://alias.example.com:8080?header=bad|:skip|X-Empty:&skip_cert_verify=no&peer=peer.example.com", map[string]any{"sni": "peer.example.com", "skip-cert-verify": false, "headers": map[string]any{"X-Empty": ""}}, nil, ""},
		{"IPv6 explicit port", "http://[2001:db8::1]:8080#IPv6", map[string]any{"name": "IPv6", "server": "2001:db8::1", "port": 8080}, nil, ""},
		{"IPv6 default port", "http://[2001:db8::2]", map[string]any{"name": "HTTP-2001:db8::2:80", "server": "2001:db8::2", "port": 80}, nil, ""},
		{"verification overrides insecure aliases", "http://verified.example.com:8080?allow-insecure=yes&tls-verification=true#Verified", map[string]any{"name": "Verified", "skip-cert-verify": false}, nil, ""},
		{"empty headers", "http://empty-headers.example.com:8080?headers= ", nil, []string{"headers"}, ""},
		{"pipe headers skip empty segments", "http://pipe.example.com:8080?headers=A:B||C:D", map[string]any{"headers": map[string]any{"A": "B", "C": "D"}}, nil, ""},
		{"encoded authority query remark", "http://" + subBoostBase64URL("http-base.example.com:8080") + "?remark=EncodedName", map[string]any{"name": "EncodedName", "server": "http-base.example.com", "port": 8080}, nil, ""},
		{"encoded username only", "http://" + subBoostBase64URL("alice@base64-user.example.com:8080") + "#EncodedUser", map[string]any{"name": "EncodedUser", "server": "base64-user.example.com", "username": "alice"}, []string{"password"}, ""},
		{"encoded hash name", "http://" + subBoostBase64URL("hash-base.example.com:8080") + "#HashName", map[string]any{"name": "HashName", "server": "hash-base.example.com"}, nil, ""},
		{"encoded suffix name", "http://" + subBoostBase64URL("suffix-base.example.com:8080") + "{SuffixName}", map[string]any{"name": "SuffixName", "server": "suffix-base.example.com"}, nil, ""},
		{"terminal unescaped JSON is a name suffix", `http://number-header.example.com:8080?headers={"A":1}`, map[string]any{"name": `"A":1`}, []string{"headers"}, ""},
		{"empty boolean and TLS name", "http://empty-bool.example.com:8080?allow-insecure=&tls-name=tls.example.com", map[string]any{"name": "HTTP-empty-bool.example.com:8080", "sni": "tls.example.com"}, []string{"skip-cert-verify"}, ""},
		{"regression nested JSON headers ignored", "https://edge.example.com:443?tls-verification=false&headers=" + url.QueryEscape(`{"Nested":{"Host":"edge.example.com"}}`), map[string]any{"skip-cert-verify": true}, []string{"headers"}, ""},
		{"regression Host pair and allow insecure", "https://http.example.com:443?allow-insecure=yes&headers=Host: edge.example.com|Bad", map[string]any{"type": "https", "tls": true, "skip-cert-verify": true, "headers": map[string]any{"Host": "edge.example.com"}}, nil, ""},
		{"regression encoded IPv6 authority and headers", "http://" + subBoostBase64("user:pw@[2001:db8::8]:8080") + "?remarks=IPv6%20Base64&headers=Host: edge.example.com||X-Test: ok&skip-cert-verify=", map[string]any{"name": "IPv6 Base64", "server": "2001:db8::8", "port": 8080, "username": "user", "password": "pw", "headers": map[string]any{"Host": "edge.example.com", "X-Test": "ok"}}, []string{"skip-cert-verify"}, ""},
		{"regression colon empty auth", "http://colon.example.com:8080::", map[string]any{"server": "colon.example.com", "port": 8080}, []string{"username", "password"}, ""},
		{"regression username and skip verification", "http://user@http-user.example.com?skip-cert-verify=off&headers=%7B%22Bad%22%3A1%7D", map[string]any{"username": "user", "skip-cert-verify": false, "headers": map[string]any{"Bad": "1"}}, nil, ""},
		{"regression colon encoded credentials", "http://encoded.example.com:8080:u%3Aser:p%40ss", map[string]any{"username": "u:ser", "password": "p@ss"}, nil, ""},
		{"regression decoded authority without port", "http://" + missingPort + "?remarks=NoPort", map[string]any{"name": "HTTP-" + missingPort + ":80", "server": missingPort, "port": 80}, nil, ""},
		{"regression control authority ignores query name", "http://" + control + "?remarks=RawToken", map[string]any{"name": "HTTP-" + control + ":80", "server": control, "port": 80}, nil, ""},
		{"regression padded standard base64", "http://dXNlcjpwYXNzQGJhc2UuZXhhbXBsZS5jb206ODA=?remarks=BaseName", map[string]any{"name": "BaseName", "username": "user", "password": "pass", "server": "base.example.com", "port": 80}, nil, ""},
		{"regression base64 auth JSON and bool", "http://" + subBoostBase64URL("proxyUser:proxyPass@base64.example.com:8080") + "?remarks=Query%20Name&skip-cert-verify=off&headers=" + jsonHeaders, map[string]any{"name": "Query Name", "username": "proxyUser", "password": "proxyPass", "skip-cert-verify": false, "headers": map[string]any{"User-Agent": "SubBoost", "X-Test": "yes"}}, nil, ""},
		{"regression invalid header segments", "http://plain.example.com?headers=bad|:skip|X-Test:yes&tls-verification=true", map[string]any{"name": "HTTP-plain.example.com:80", "port": 80, "skip-cert-verify": false, "headers": map[string]any{"X-Test": "yes"}}, nil, ""},
		{"JSON primitive values and trimmed keys", "http://h?headers=" + url.QueryEscape(`{" A ":"  v  ","Empty":"","N":2.5,"B":false,"skip":{},"array":[],"null":null," ":"ignored"}`), map[string]any{"headers": map[string]any{"A": "  v  ", "Empty": "", "N": "2.5", "B": "false"}}, nil, ""},
		{"JSON number formatting", "http://h?headers=" + url.QueryEscape(`{"Million":1000000,"Tiny":0.0000001,"Zero":-0}`), map[string]any{"headers": map[string]any{"Million": "1000000", "Tiny": "1e-7", "Zero": "0"}}, nil, ""},
		{"standard URL ignores query remarks", "http://plain.example.com?remarks=Ignored", map[string]any{"name": "HTTP-plain.example.com:80", "port": 80, "tls": false}, nil, ""},
		{"suffix beats hash and encoded query name", "http://" + subBoostBase64URL("h:80") + "?remark=Query#Hash[refresh]{Suffix+Name}", map[string]any{"name": "Suffix Name", "server": "h"}, nil, ""},
		{"refresh suffix and empty name", "http://h:80[https://refresh.example/sub]{}", map[string]any{"name": "HTTP-h:80", "server": "h", "port": 80}, nil, ""},
		{"encoded invalid port stays token", "http://" + subBoostBase64URL("h:bad") + "?remark=Ignored", map[string]any{"server": subBoostBase64URL("h:bad"), "name": "HTTP-" + subBoostBase64URL("h:bad") + ":80"}, nil, ""},
		{"encoded signed numeric prefix port", "http://" + subBoostBase64URL("h:+8080tail"), map[string]any{"server": "h", "port": 8080}, nil, ""},
		{"colon branch ignores all query options", "http://h:8080tail:u:p:a?headers=A:B&sni=ignored&allow-insecure=yes", map[string]any{"port": 8080, "username": "u", "password": "p:a"}, []string{"headers", "sni", "skip-cert-verify"}, ""},
		{"array JSON falls back to header pairs", "http://h?headers=" + url.QueryEscape(`["A:B"]`), map[string]any{"headers": map[string]any{`["A`: `B"]`}}, nil, ""},
		{"truthy whitespace blocks string aliases", "http://h?headers=+&header=X:y&sni=+&peer=ignored&allow-insecure=bad&insecure=off", map[string]any{"skip-cert-verify": false}, []string{"headers", "sni"}, ""},
		{"encoded query name is form decoded twice", "http://" + subBoostBase64URL("h:80") + "?remarks=Ignored&remark=A%2520+B&remark=Later", map[string]any{"name": "A  B"}, nil, ""},
		{"encoded fragment question mark keeps compatibility query", "http://" + subBoostBase64URL("h:80") + "#Hash?sni=edge", map[string]any{"name": "Hash?sni=edge", "sni": "edge"}, nil, ""},
		{"standard credentials decode once", "http://u%2520:p%3A%2B%2520@h#%2520%2B+%E4%B8%AD", map[string]any{"username": "u%20", "password": "p:+%20", "name": "%20+ 中"}, nil, ""},
		{"large port", "http://h:70000", nil, nil, "无效的端口号"},
		{"hex port prefix is zero", "http://h:0x10", nil, nil, "无效的端口号"},
		{"bad colon port", "http://badcolon.example.com:x:user:pass", nil, nil, "无效的端口号"},
		{"missing colon server", "http://:8080:user:pass", nil, nil, "缺少服务器地址"},
		{"missing standard server", "http://:80", nil, nil, "缺少服务器地址"},
		{"unsupported FTP scheme", "ftp://example.com:21", nil, nil, "不支持的协议: ftp"},
		{"unsupported custom scheme", "custom://example.com:80", nil, nil, "不支持的协议: custom"},
		{"no naked entry point", "user:pass@host.example.com:8080", nil, nil, "无效的代理格式"},
	})
}

func TestSSHLinks(t *testing.T) {
	testSimpleProxyLinks(t, parseSSHLink, []simpleProxyTestCase{
		{"keys fingerprint timeout algorithms and insecure", "ssh://root:secret@ssh.example.com:22?private-key=KEY&host-key=a,b&server-fingerprint=fp&idle-timeout=30&host-key-algorithms=ssh-ed25519|rsa&allow-insecure=1#SSH", map[string]any{"name": "SSH", "type": "ssh", "server": "ssh.example.com", "port": 22, "username": "root", "password": "secret", "private-key": "KEY", "host-key": []string{"a", "b"}, "server-fingerprint": "fp", "idle-timeout": 30, "host-key-algorithms": []string{"ssh-ed25519", "rsa"}, "skip-cert-verify": true}, []string{"tls", "udp", "sni"}, ""},
		{"key aliases and invalid timeout", "ssh://alias@ssh-alias.example.com?private_key=KEY&hostKey=a;b&idle_timeout=bad#AliasSSH", map[string]any{"name": "AliasSSH", "server": "ssh-alias.example.com", "port": 22, "username": "alias", "private-key": "KEY", "host-key": []string{"a", "b"}}, []string{"idle-timeout"}, ""},
		{"empty host key omitted", "ssh://ssh-empty.example.com?host-key=,&private-key=KEY#EmptyHostKey", map[string]any{"name": "EmptyHostKey", "private-key": "KEY"}, []string{"host-key"}, ""},
		{"minimal defaults", "ssh://ssh-minimal.example.com", map[string]any{"name": "SSH-ssh-minimal.example.com:22", "server": "ssh-minimal.example.com", "port": 22, "type": "ssh"}, []string{"username", "password", "tls", "udp"}, ""},
		{"regression whitespace in host keys", "ssh://admin@ssh.example.com?private-key=key&host-key=ssh-rsa%20aaa,ssh-ed25519%20bbb&idle-timeout=30&host-key-algorithms=rsa,ed25519&server-fingerprint=sha256:abc&allowInsecure=yes", map[string]any{"name": "SSH-ssh.example.com:22", "username": "admin", "private-key": "key", "host-key": []string{"ssh-rsa aaa", "ssh-ed25519 bbb"}, "idle-timeout": 30, "host-key-algorithms": []string{"rsa", "ed25519"}, "server-fingerprint": "sha256:abc", "skip-cert-verify": true}, nil, ""},
		{"regression pipe host keys", "ssh://root:ssh-pass@ssh.example.com:22?host-key=sha1|sha2&idle-timeout=30", map[string]any{"username": "root", "password": "ssh-pass", "host-key": []string{"sha1", "sha2"}, "idle-timeout": 30}, nil, ""},
		{"regression empty private key", "ssh://ssh.example.com:22?private-key=&host-key=ssh-ed25519%20AAA&allow-insecure=yes", map[string]any{"host-key": []string{"ssh-ed25519 AAA"}, "skip-cert-verify": true}, []string{"private-key"}, ""},
		{"regression empty lists bad timeout and false bool", "ssh://ssh.example.com?host-key=,,;&idle-timeout=abc&allow-insecure=off", map[string]any{"type": "ssh", "server": "ssh.example.com", "skip-cert-verify": false}, []string{"host-key", "idle-timeout"}, ""},
		{"camel and underscore aliases", "ssh://h?privateKey=+KEY+&host_key=+a%3Bb+&server_fingerprint=+fp+&idle_timeout=00030&host_key_algorithms=+a%7Cb+", map[string]any{"private-key": "KEY", "host-key": []string{"a", "b"}, "server-fingerprint": "fp", "idle-timeout": 30, "host-key-algorithms": []string{"a", "b"}}, nil, ""},
		{"algorithm separator only stays empty array", "ssh://h?host-key=,;&host-key-algorithms=,;", map[string]any{"host-key-algorithms": []string{}}, []string{"host-key"}, ""},
		{"invalid primary values block aliases", "ssh://h?private-key=+&privateKey=ignored&idle-timeout=bad&idle_timeout=30&host-key=+&hostKey=ignored&sni=ignored&tls-verification=false", nil, []string{"private-key", "host-key", "idle-timeout", "sni", "skip-cert-verify"}, ""},
		{"zero timeout and password only", "ssh://:secret@[2001:db8::1]?idle-timeout=0", map[string]any{"name": "SSH-2001:db8::1:22", "password": "secret", "idle-timeout": 0}, []string{"username"}, ""},
		{"integer prefix is not a valid SSH timeout", "ssh://h?idle-timeout=30ms", nil, []string{"idle-timeout"}, ""},
		{"overflowing timeout omitted", "ssh://h?idle-timeout=999999999999999999999999999999", nil, []string{"idle-timeout"}, ""},
		{"encoded authority uses SSH type", "ssh://" + subBoostBase64URL("root:p@h:2222") + "?private-key=KEY&idle-timeout=5#Encoded", map[string]any{"name": "Encoded", "type": "ssh", "server": "h", "port": 2222, "username": "root", "password": "p", "private-key": "KEY", "idle-timeout": 5}, nil, ""},
		{"colon auth uses SSH type and skips query", "ssh://h:2222:u:p?private-key=ignored", map[string]any{"type": "ssh", "username": "u", "password": "p", "port": 2222}, []string{"private-key"}, ""},
		{"missing server", "ssh://:22", nil, nil, "缺少服务器地址"},
		{"no naked entry point", "root@ssh.example.com:22", nil, nil, "无效的代理格式"},
	})
}
