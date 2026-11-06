// 移植 SubBoost 4015347 canonical-fields 与 protocol-fields 的 YAML 后处理。
package flight

import (
	"encoding/base64"
	"fmt"
	"regexp"
	"slices"
	"strconv"
	"strings"
)

var (
	yamlCertificateLabel  = regexp.MustCompile(`(?i)^sha256\s+fingerprint\s*=\s*`)
	yamlCertificatePrefix = regexp.MustCompile(`(?i)^sha256[:=]\s*`)
)

func yamlCertificateHash(value any) bool {
	text, ok := value.(string)
	if !ok {
		return false
	}
	text = yamlCertificateLabel.ReplaceAllString(strings.TrimSpace(text), "")
	text = yamlCertificatePrefix.ReplaceAllString(text, "")
	text = strings.ReplaceAll(text, ":", "")
	return certificateFingerprint.MatchString(text)
}

func yamlAlias(node map[string]any, canonical []string, aliases ...[]string) {
	// 证书 SHA-256 和客户端指纹是不同字段，带 sha256 标签的值也不能被别名吞掉。
	if len(canonical) == 1 && canonical[0] == "client-fingerprint" && yamlCertificateHash(node["fingerprint"]) {
		aliases = slices.DeleteFunc(aliases, func(path []string) bool { return len(path) == 1 && path[0] == "fingerprint" })
	}
	value := getPath(node, canonical...)
	for _, candidate := range aliases {
		if !hasValue(value) {
			value = getPath(node, candidate...)
		}
	}
	if !hasValue(value) {
		return
	}
	alias(node, canonical, aliases...)
}

func canonicalizeYAMLAliases(node map[string]any, typ string) {
	canonicalizeNodeAliases(node, typ, yamlAlias)
	if typ == "vless" {
		yamlAlias(node, []string{"reality-opts", "support-x25519mlkem768"},
			[]string{"reality-opts", "supportX25519MLKEM768"}, []string{"reality-opts", "support_x25519mlkem768"})
	}
	if typ == "wireguard" {
		yamlAlias(node, []string{"private-key"}, []string{"privatekey"}, []string{"privateKey"}, []string{"private_key"})
		yamlAlias(node, []string{"public-key"}, []string{"publickey"}, []string{"publicKey"}, []string{"public_key"}, []string{"peer-public-key"}, []string{"peerPublicKey"})
		yamlAlias(node, []string{"pre-shared-key"}, []string{"presharedkey"}, []string{"preshared-key"}, []string{"preSharedKey"}, []string{"pre_shared_key"}, []string{"psk"})
	}
}

func normalizeYAMLProtocolFields(node map[string]any, typ string) error {
	if typ == "snell" {
		normalizeYAMLSnell(node)
	}
	if typ != "wireguard" {
		return nil
	}
	// JS 的 ?? 链最后一项即使是 null 也会参与校验，缺字段才是 undefined。
	raw, present := node["persistentKeepalive"]
	for _, key := range []string{"persistent-keepalive", "keepalive", "persistent_keepalive"} {
		if node[key] != nil {
			raw, present = node[key], true
			break
		}
	}
	if present {
		value, ok := yamlUnsignedInteger(raw, 65535)
		if !ok {
			return fmt.Errorf("invalid wireguard persistent-keepalive")
		}
		node["persistent-keepalive"] = value
	}
	for _, key := range []string{"keepalive", "persistent_keepalive", "persistentKeepalive"} {
		delete(node, key)
	}
	normalizeYAMLReserved(node)
	if peers, ok := node["peers"].([]any); ok {
		for _, raw := range peers {
			if peer, ok := raw.(map[string]any); ok {
				normalizeYAMLReserved(peer)
			}
		}
	}
	return nil
}

func yamlUnsignedInteger(value any, max int) (int, bool) {
	if text, ok := value.(string); ok {
		text = strings.TrimSpace(text)
		if text == "" {
			return 0, false
		}
		for _, r := range text {
			if r < '0' || r > '9' {
				return 0, false
			}
		}
		n, err := strconv.Atoi(text)
		return n, err == nil && n <= max
	}
	n, ok := portValue(value)
	return n, ok && n >= 0 && n <= max
}

func normalizeYAMLReserved(node map[string]any) {
	raw, exists := node["reserved"]
	if !exists {
		return
	}
	delete(node, "reserved")
	var parts []any
	switch value := raw.(type) {
	case []any:
		parts = value
	case string:
		text := strings.TrimSpace(value)
		if len(text) == 4 {
			if decoded, err := base64.StdEncoding.DecodeString(text); err == nil && len(decoded) == 3 {
				node["reserved"] = []any{int(decoded[0]), int(decoded[1]), int(decoded[2])}
				return
			}
		}
		for _, part := range strings.Split(value, ",") {
			parts = append(parts, part)
		}
	}
	if len(parts) != 3 {
		return
	}
	out := make([]any, 3)
	for i, part := range parts {
		n, ok := yamlUnsignedInteger(part, 255)
		if !ok {
			return
		}
		out[i] = n
	}
	node["reserved"] = out
}

func normalizeYAMLSnell(node map[string]any) {
	fields := map[string]string{"shadow-tls-version": "version", "shadow-tls-sni": "host", "shadow-tls-password": "password"}
	existing, exists := node["obfs-opts"]
	opts, object := existing.(map[string]any)
	if !exists || object && opts["mode"] == "shadow-tls" {
		out := map[string]any{"mode": "shadow-tls"}
		found := false
		for from, to := range fields {
			if value, ok := node[from]; ok {
				out[to] = value
				found = true
			}
		}
		if found {
			for key, value := range opts {
				out[key] = value
			}
			node["obfs-opts"] = out
		}
	}
	for key := range fields {
		delete(node, key)
	}
}
