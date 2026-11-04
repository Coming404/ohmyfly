// Ported from SubBoost packages/core/src/parser/protocols/hysteria.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

// mihomoBandwidthPattern 是 mihomo v1.19.33 common/utils/mbps.go 的 rateStringRegexp，单位前缀只认大写。
var mihomoBandwidthPattern = regexp.MustCompile(`^(\d+)\s*([KMGT]?)([Bb])ps$`)

// hysteriaBandwidthPattern 按 Hysteria 官方 StringToBps（app/internal/utils/bpsconv.go）识别带宽，单位不区分大小写。
var hysteriaBandwidthPattern = regexp.MustCompile(`^(\d+)\s*([A-Za-z]+)$`)

var hysteriaBandwidthUnits = map[string]string{
	"b": "bps", "bps": "bps",
	"k": "Kbps", "kb": "Kbps", "kbps": "Kbps",
	"m": "Mbps", "mb": "Mbps", "mbps": "Mbps",
	"g": "Gbps", "gb": "Gbps", "gbps": "Gbps",
	"t": "Tbps", "tb": "Tbps", "tbps": "Tbps",
}

// hysteriaBandwidth 让带宽能被 mihomo 读取。有意偏离上游：上游给 Hysteria2 纯数字补的 " mbps"
// 和原样保留的 "1gbps"，mihomo 都读成 0（v1 因此报错，v2 不限速）。mihomo 已能读的写法原样保留，
// 其余按 Hysteria 官方单位改写；纯数字在 v1 保留（mihomo 按 Mbps 读），在 v2 按上游补单位。
func hysteriaBandwidth(value string, version2 bool) string {
	if mihomoBandwidthPattern.MatchString(value) {
		return value
	}
	trimmed := jsTrimSpace(value)
	if hysteriaDigits.MatchString(trimmed) {
		if version2 {
			return trimmed + " Mbps"
		}
		return trimmed
	}
	match := hysteriaBandwidthPattern.FindStringSubmatch(trimmed)
	if match == nil {
		return value
	}
	if unit, ok := hysteriaBandwidthUnits[strings.ToLower(match[2])]; ok {
		return match[1] + " " + unit
	}
	return value
}

func parseHysteriaLink(raw string) (map[string]any, error) {
	if !strings.HasPrefix(raw, "hysteria://") && !strings.HasPrefix(raw, "hy://") {
		return nil, fmt.Errorf("invalid hysteria link")
	}
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	server, port, err := linkAuthority(parsed, 443)
	if err != nil {
		return nil, err
	}
	node := map[string]any{
		"name":     linkName(parsed.Fragment, "Hysteria "+server+":"+strconv.Itoa(port)),
		"type":     "hysteria",
		"server":   server,
		"port":     port,
		"protocol": cmp.Or(query.Get("protocol"), "udp"),
	}
	auth := pickQueryParam(
		query,
		"auth",
		"auth_str",
		"auth-str",
		"password",
	)
	if auth = jsTrimSpace(auth); auth != "" {
		node["auth-str"] = auth
	}
	if err := applyHysteriaOptions(node, query); err != nil {
		return nil, err
	}
	for _, key := range []string{"fast-open", "fast_open", "fastOpen"} {
		if query.Has(key) {
			if value, ok := linkBool(query.Get(key)); ok {
				node["fast-open"] = value
			}
			break
		}
	}
	if value, ok := linkBool(query.Get("tfo")); ok {
		node["tfo"] = value
	}
	return node, nil
}

func applyHysteriaOptions(node map[string]any, query url.Values) error {
	version2 := node["type"] == "hysteria2"
	sni := pickQueryParam(query, "sni", "peer")
	if version2 && sni == "" {
		sni = node["server"].(string)
	}
	if sni != "" {
		node["sni"] = sni
	}
	if version2 {
		if linkStrictTrue(
			query,
			"insecure",
			"allowInsecure",
			"allow_insecure",
		) {
			node["skip-cert-verify"] = true
		}
	} else if value, ok := linkOptionalBool(query, "insecure", "allowInsecure"); ok {
		node["skip-cert-verify"] = value
	}
	addLinkAlpn(node, query.Get("alpn"))
	for _, field := range []string{"up", "down"} {
		primary, secondary := field+"mbps", field
		if version2 {
			primary, secondary = secondary, primary
		}
		if value := pickQueryParam(query, primary, secondary); value != "" {
			node[field] = hysteriaBandwidth(value, version2)
		}
	}
	if !version2 {
		if ports := hysteriaPorts(query, "", false); ports != "" {
			node["ports"] = ports
		}
	}
	return applyHysteriaObfs(node, query)
}

func applyHysteriaObfs(node map[string]any, query url.Values) error {
	if node["type"] == "hysteria" {
		if value := pickQueryParam(query, "obfsParam", "obfs-param"); value != "" {
			node["obfs"] = value
		}
		if query.Has("obfs") {
			node["_obfs"] = query.Get("obfs")
		}
		return nil
	}
	obfs := strings.ToLower(jsTrimSpace(query.Get("obfs")))
	switch obfs {
	case "", "none":
		return nil
	case "salamander", "gecko":
		password := pickQueryParam(
			query,
			"obfs-password",
			"obfs_password",
			"obfsPassword",
		)
		if password == "" {
			return fmt.Errorf("hysteria2 %s obfs requires obfs-password", obfs)
		}
		node["obfs"] = obfs
		node["obfs-password"] = password
		return nil
	default:
		return fmt.Errorf("unsupported hysteria2 obfs: %s", obfs)
	}
}

func hysteriaPorts(query url.Values, authority string, version2 bool) string {
	if !version2 {
		return pickQueryParam(query, "mport", "ports")
	}
	ports := jsTrimSpace(pickQueryParam(query, "ports", "mport"))
	if ports == "" && !hysteriaDigits.MatchString(authority) {
		ports = authority
	}
	return normalizeLinkPortsSpec(ports)
}
