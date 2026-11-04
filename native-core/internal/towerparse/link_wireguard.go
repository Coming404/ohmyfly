// Ported from SubBoost packages/core/src/parser/protocols/wireguard.ts @4015347 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/mihomo/protocol-fields.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var wireGuardReservedBase64Pattern = regexp.MustCompile(`^[A-Za-z0-9+/]{4}$`)

func parseWireGuardLink(raw string) (map[string]any, error) {
	if !strings.HasPrefix(raw, "wireguard://") && !strings.HasPrefix(raw, "wg://") {
		return nil, fmt.Errorf("无效的 WireGuard 链接")
	}
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	privateKey := cmp.Or(parsed.Username, pickQueryParam(
		query,
		"private-key",
		"private_key",
		"privateKey",
	))
	server := parsed.Hostname
	if server == "" || privateKey == "" {
		return nil, fmt.Errorf("WireGuard 配置缺少必要字段")
	}
	port, err := linkPort(parsed.Port, 51820)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}
	node := map[string]any{
		"name": linkName(parsed.Fragment, "WireGuard "+server+":"+strconv.Itoa(port)),
		"type": "wireguard", "server": server, "port": port,
		"private-key": privateKey, "udp": true,
	}
	// Alias overrides depend on URLSearchParams entry order, which url.Values loses.
	for pair := range strings.SplitSeq(strings.TrimPrefix(parsed.RawQuery, "?"), "&") {
		rawKey, rawValue, _ := strings.Cut(pair, "=")
		key := jsTrimSpace(strings.ReplaceAll(linkFormComponent(rawKey), "_", "-"))
		value := linkDecodeComponent(linkFormComponent(rawValue))
		if key == "" || value == "" {
			continue
		}
		lowerKey := strings.ToLower(key)
		switch {
		case key == "reserved":
			if reserved, ok := normalizeWireGuardLinkReserved(value); ok {
				node["reserved"] = reserved
			}
		case key == "address" || key == "ip":
			assignWireGuardAddress(node, value)
		case key == "mtu":
			if mtu, ok := linkIntegerPrefix(url.Values{key: {value}}, key); ok {
				node["mtu"] = mtu
			}
		case strings.Contains(lowerKey, "publickey") || key == "peer-public-key":
			node["public-key"] = value
		case key == "pre-shared-key", key == "preshared-key", key == "presharedkey":
			node["pre-shared-key"] = value
		case strings.Contains(lowerKey, "privatekey"):
			node["private-key"] = value
		case key == "udp":
			if udp, ok := linkBool(value); ok {
				node["udp"] = udp
			}
		default:
			// Upstream's `key in node` also sees Object.prototype properties.
			switch key {
			case "flag", "constructor", "toString", "toLocaleString", "valueOf", "hasOwnProperty",
				"isPrototypeOf", "propertyIsEnumerable":
				continue
			}
			if _, exists := node[key]; !exists {
				if typed, ok := wireGuardTypedValue(key, value); ok {
					node[key] = typed
				}
			}
		}
	}
	if err := normalizeWireGuardLinkKeepalive(node); err != nil {
		return nil, err
	}
	return node, nil
}

// wireGuardTypedValue 把透传参数转成 mihomo WireGuardOption 的字段类型。有意偏离上游：上游原样写字符串，
// mihomo 解码 bool、[]string 字段时遇到字符串直接报错，节点无法加载（common/structure/structure.go）。
// 无法转换的值丢弃；其余参数仍原样透传。
func wireGuardTypedValue(key, value string) (any, bool) {
	switch key {
	case "tfo", "mptcp", "remote-dns-resolve":
		return linkBool(value)
	case "dns", "allowed-ips":
		values := linkList(value)
		return values, len(values) > 0
	}
	return value, true
}

func assignWireGuardAddress(node map[string]any, raw string) {
	for item := range strings.SplitSeq(raw, ",") {
		address := jsTrimSpace(item)
		if host, prefix, found := strings.CutLast(address, "/"); found && isLinkDigitString(prefix) {
			address = host
		}
		address = strings.TrimSuffix(strings.TrimPrefix(address, "["), "]")
		if address == "" {
			continue
		}
		field := "ip"
		if strings.Contains(address, ":") {
			field = "ipv6"
		}
		node[field] = address
	}
}

func normalizeWireGuardLinkReserved(raw string) ([]int, bool) {
	if wireGuardReservedBase64Pattern.MatchString(jsTrimSpace(raw)) {
		decoded, _ := decodeLinkBase64(jsTrimSpace(raw))
		return []int{int(decoded[0]), int(decoded[1]), int(decoded[2])}, true
	}
	parts := strings.Split(raw, ",")
	if len(parts) != 3 {
		return nil, false
	}
	reserved := make([]int, 0, 3)
	for _, part := range parts {
		part = jsTrimSpace(part)
		number, err := strconv.Atoi(part)
		validByte := isLinkDigitString(part) && err == nil && number <= 255
		if !validByte {
			return nil, false
		}
		reserved = append(reserved, number)
	}
	return reserved, true
}

func normalizeWireGuardLinkKeepalive(node map[string]any) error {
	for _, key := range []string{"persistent-keepalive", "keepalive", "persistentKeepalive"} {
		value, exists := node[key]
		if !exists {
			continue
		}
		raw := jsTrimSpace(value.(string))
		number, err := strconv.Atoi(raw)
		validKeepalive := isLinkDigitString(raw) && err == nil && number <= 65535
		if !validKeepalive {
			return fmt.Errorf("WireGuard persistent-keepalive 无效")
		}
		node["persistent-keepalive"] = number
		break
	}
	delete(node, "keepalive")
	delete(node, "persistentKeepalive")
	return nil
}
