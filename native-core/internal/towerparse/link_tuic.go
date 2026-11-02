// Ported from SubBoost packages/core/src/parser/protocols/tuic.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var tuicUUIDPattern = regexp.MustCompile(
	`(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`,
)

func parseTUICLink(raw string) (map[string]any, error) {
	if !strings.HasPrefix(raw, "tuic://") {
		return nil, fmt.Errorf("invalid tuic link")
	}
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	server, port, err := linkAuthority(parsed, 443)
	if err != nil {
		return nil, err
	}
	username, userPassword := parsed.Username, parsed.Password
	uuid := linkString(
		query,
		"uuid",
		"user",
		"username",
	)
	if userPassword != "" || tuicUUIDPattern.MatchString(jsTrimSpace(username)) {
		uuid = username
	}
	password := cmp.Or(userPassword, linkString(query, "password", "passwd"))
	token := linkString(
		query,
		"token",
		"auth",
		"auth_str",
		"auth-str",
	)
	useUsernameToken := userPassword == "" && password == ""
	if token == "" && useUsernameToken {
		token = username
	}
	if token == "" && (uuid == "" || password == "") {
		return nil, fmt.Errorf("missing token or uuid/password")
	}
	node := map[string]any{
		"name":   linkName(parsed.Fragment, linkDefaultName(server, port)),
		"type":   "tuic",
		"server": server,
		"port":   port,
	}
	if token != "" {
		node["token"] = token
	} else {
		node["uuid"] = uuid
		node["password"] = password
	}
	applyTUICOptions(node, query)
	return node, nil
}

func applyTUICOptions(node map[string]any, query url.Values) {
	if value := pickQueryParam(query, "sni", "peer"); value != "" {
		node["sni"] = value
	}
	addLinkAlpn(node, query.Get("alpn"))
	for _, field := range []linkQueryAlias{
		{target: "congestion-controller", keys: []string{"congestion_control", "congestion-control", "congestionControl"}},
		{target: "udp-relay-mode", keys: []string{"udp_relay_mode", "udp-relay-mode", "udpRelayMode"}},
	} {
		if value := linkString(query, field.keys...); value != "" {
			node[field.target] = value
		}
	}
	for _, field := range []linkQueryAlias{
		{target: "request-timeout", keys: []string{"request-timeout", "request_timeout", "requestTimeout"}},
		{target: "heartbeat-interval", keys: []string{"heartbeat-interval", "heartbeat_interval", "heartbeatInterval"}},
		{target: "max-open-streams", keys: []string{"max-open-streams", "max_open_streams", "maxOpenStreams"}},
		{target: "max-idle-time", keys: []string{"max-idle-time", "max_idle_time", "maxIdleTime"}},
	} {
		if value, ok := linkIntegerPrefix(query, field.keys...); ok {
			node[field.target] = value
		}
	}
	for _, field := range []linkQueryAlias{
		{target: "skip-cert-verify", keys: []string{"allow_insecure", "allowInsecure", "allow-insecure", "insecure"}},
		{target: "disable-sni", keys: []string{"disable_sni", "disable-sni", "disableSni"}},
		{target: "reduce-rtt", keys: []string{"reduce_rtt", "reduce-rtt", "reduceRtt"}},
	} {
		if linkStrictTrue(query, field.keys...) {
			node[field.target] = true
		}
	}
	if value, ok := linkOptionalBool(
		query,
		"fast-open",
		"fast_open",
		"fastOpen",
		"tfo",
	); ok {
		node["fast-open"] = value
	}
	if value, ok := linkBool(query.Get("tfo")); ok {
		node["tfo"] = value
	}
}
