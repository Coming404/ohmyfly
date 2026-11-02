// Ported from SubBoost packages/core/src/parser/protocols/hysteria.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var hysteriaBandwidthUnit = regexp.MustCompile(`(?i)[kmg]?bps$`)

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
		"name":     linkName(parsed.Fragment, linkDefaultName(server, port)),
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
		value := pickQueryParam(query, primary, secondary)
		if value == "" {
			continue
		}
		if version2 && !hysteriaBandwidthUnit.MatchString(value) {
			value += " mbps"
		}
		node[field] = value
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
