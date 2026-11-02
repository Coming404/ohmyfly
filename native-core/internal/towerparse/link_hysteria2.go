// Ported from SubBoost packages/core/src/parser/protocols/hysteria2.ts @4015347 (AGPL-3.0-only).
// Ported from SubBoost packages/core/src/parser/port-spec.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"unicode"
)

var hysteriaDigits = regexp.MustCompile(`^\d+$`)

func parseHysteria2Link(raw string) (map[string]any, error) {
	body, ok := strings.CutPrefix(raw, "hysteria2://")
	if !ok {
		body, ok = strings.CutPrefix(raw, "hy2://")
	}
	if !ok {
		return nil, fmt.Errorf("invalid hysteria2 link")
	}
	body, hash, _ := strings.Cut(body, "#")
	authority, queryString, _ := strings.Cut(body, "?")
	query := linkSearchParams(queryString)
	userInfo, server, portSpec := splitHysteria2Authority(authority)
	server = strings.TrimSpace(server)
	password := linkDecodeComponent(userInfo)
	if password == "" {
		password = strings.TrimSpace(pickQueryParam(
			query,
			"password",
			"auth",
			"auth_str",
			"auth-str",
		))
	}
	if server == "" || password == "" {
		return nil, fmt.Errorf("missing server or password")
	}
	ports := hysteriaPorts(query, portSpec, true)
	port := linkStablePortFromPorts(ports)
	if hysteriaDigits.MatchString(portSpec) {
		var err error
		if port, err = linkPort(portSpec, 443); err != nil {
			return nil, err
		}
	}
	node := map[string]any{
		"name":     linkName(hash, linkDefaultName(server, port)),
		"type":     "hysteria2",
		"server":   server,
		"port":     port,
		"password": password,
	}
	if ports != "" {
		node["ports"] = ports
	}
	if err := applyHysteriaOptions(node, query); err != nil {
		return nil, err
	}
	if err := applyHysteria2Options(node, query); err != nil {
		return nil, err
	}
	return node, nil
}

func splitHysteria2Authority(value string) (string, string, string) {
	raw := strings.TrimRight(strings.TrimSpace(value), "/")
	userInfo, hostPort, hasUser := strings.CutLast(raw, "@")
	if !hasUser {
		userInfo, hostPort = "", raw
	}
	if after, ipv6 := strings.CutPrefix(hostPort, "["); ipv6 {
		server, rest, closed := strings.Cut(after, "]")
		if closed {
			portSpec := ""
			if strings.HasPrefix(rest, ":") {
				portSpec = strings.TrimPrefix(rest, ":")
			}
			return userInfo, server, strings.TrimRight(portSpec, "/")
		}
	}
	server, portSpec, hasPort := strings.CutLast(hostPort, ":")
	if !hasPort {
		return userInfo, hostPort, ""
	}
	return userInfo, server, strings.TrimRight(portSpec, "/")
}

func applyHysteria2Options(node map[string]any, query url.Values) error {
	// Upstream: (fp || fingerprint || pinSHA256).trim(), value kept as-is.
	fingerprint := pickQueryParam(query, "fp", "fingerprint", "pinSHA256")
	if fingerprint = strings.TrimSpace(fingerprint); fingerprint != "" {
		node["fingerprint"] = fingerprint
	}
	for _, field := range []string{"obfs-min-packet-size", "obfs-max-packet-size"} {
		key := field
		if !query.Has(key) {
			key = strings.ReplaceAll(field, "-", "_")
		}
		if !query.Has(key) {
			continue
		}
		raw := query.Get(key)
		value, ok := xhttpIntegerValue(raw)
		if !hysteriaDigits.MatchString(raw) || !ok {
			return fmt.Errorf("hysteria2 %s requires a non-negative safe integer", field)
		}
		node[field] = value
	}
	if query.Has("ech") {
		node["ech-opts"] = linkECHOpts(query.Get("ech"))
	}
	if value, ok := hysteriaHopInterval(query); ok {
		node["hop-interval"] = value
	}
	if value := pickQueryParam(
		query,
		"mldsa65-seed",
		"mldsa65_seed",
		"mldsa65Seed",
	); value != "" {
		node["mldsa65-seed"] = value
	}
	return nil
}

func hysteriaHopInterval(query url.Values) (any, bool) {
	for _, key := range []string{"hop-interval", "hop_interval", "hopInterval"} {
		raw := strings.TrimSpace(query.Get(key))
		from, to, ranged := strings.Cut(raw, "-")
		if !hysteriaDigits.MatchString(from) {
			continue
		}
		minimum, err := strconv.Atoi(from)
		if err != nil || minimum <= 0 {
			continue
		}
		if !ranged {
			return minimum, true
		}
		if !hysteriaDigits.MatchString(to) {
			continue
		}
		maximum, err := strconv.Atoi(to)
		if err == nil && maximum >= minimum {
			return strconv.Itoa(minimum) + "-" + strconv.Itoa(maximum), true
		}
	}
	return nil, false
}

func normalizeLinkPortsSpec(value string) string {
	return strings.Map(func(char rune) rune {
		switch {
		case char == ';' || char == '/':
			return ','
		case unicode.IsSpace(char):
			return -1
		default:
			return char
		}
	}, value)
}

func linkStablePortFromPorts(ports string) int {
	first := 0
	for part := range strings.SplitSeq(ports, ",") {
		from, to, ranged := strings.Cut(part, "-")
		if !hysteriaDigits.MatchString(from) {
			continue
		}
		minimum, err := linkPort(from, 443)
		if err != nil {
			continue
		}
		maximum := minimum
		if ranged {
			if !hysteriaDigits.MatchString(to) {
				continue
			}
			maximum, err = linkPort(to, 443)
			if err != nil || maximum < minimum {
				continue
			}
		}
		if minimum <= 443 && maximum >= 443 {
			return 443
		}
		if first == 0 {
			first = minimum
		}
	}
	return cmp.Or(first, 443)
}
