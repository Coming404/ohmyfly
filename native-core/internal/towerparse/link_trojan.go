// Ported from SubBoost packages/core/src/parser/protocols/trojan.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"strings"
)

func parseTrojanLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "trojan")
	parsed, query, err := linkURL(raw)
	if err != nil {
		return nil, err
	}
	server, port, err := linkAuthority(parsed, 443)
	if err != nil {
		return nil, err
	}
	password := parsed.Username
	if parsed.Password != "" {
		password += ":" + parsed.Password
	}
	if password == "" {
		return nil, fmt.Errorf("missing password")
	}
	name := linkName(parsed.Fragment, "")
	if name == "" {
		name = linkName(linkString(query, "remarks", "remark"), linkDefaultName(server, port))
	}
	node := map[string]any{
		"name":       name,
		"type":       "trojan",
		"server":     server,
		"port":       port,
		"password":   password,
		"udp":        true,
		"servername": linkString(query, "sni", "peer"),
	}
	if node["servername"] == "" {
		node["servername"] = server
	}
	if values, exists := query["ech"]; exists {
		value := ""
		if len(values) > 0 {
			value = values[0]
		}
		node["ech-opts"] = linkECHOpts(value)
	}
	if value := linkString(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint"); value != "" {
		node["client-fingerprint"] = value
	}
	addLinkAlpn(node, linkString(query, "alpn"))
	if value, ok := linkBoolValue(query, "allowInsecure", "allow_insecure", "allow-insecure", "insecure"); ok && value {
		node["skip-cert-verify"] = true
	}
	network := strings.ToLower(linkString(query, "type"))
	if network == "" {
		if value, ok := linkBoolValue(query, "ws"); ok && value {
			network = "ws"
		} else {
			network = "tcp"
		}
	}
	if network != "tcp" && network != "ws" && network != "httpupgrade" && network != "grpc" {
		network = "tcp"
	}
	if err := applyTrojanTransport(node, trojanTransportOptions{
		network:     network,
		path:        linkString(query, "path", "wspath", "ws-path"),
		host:        linkString(query, "host", "obfsParam", "obfs-host"),
		serviceName: linkString(query, "serviceName", "service_name", "service-name"),
		mode:        linkString(query, "mode"),
		authority:   linkString(query, "authority"),
		httpUpgrade: network == "httpupgrade",
	}); err != nil {
		return nil, err
	}
	return node, nil
}

type trojanTransportOptions struct {
	network     string
	path        string
	host        string
	serviceName string
	mode        string
	authority   string
	httpUpgrade bool
}

func applyTrojanTransport(node map[string]any, options trojanTransportOptions) error {
	network := strings.ToLower(jsTrimSpace(options.network))
	path := options.path
	host := options.host
	serviceName := options.serviceName
	mode := options.mode
	authority := options.authority
	httpUpgrade := options.httpUpgrade
	switch network {
	case "", "none", "tcp":
		node["network"] = "tcp"
	case "websocket", "ws", "httpupgrade":
		node["network"] = "ws"
		path, earlyData := splitLinkEarlyData(path, "/")
		opts := map[string]any{"path": path}
		if host != "" {
			if headers := linkHeaderMap(host); headers != nil {
				opts["headers"] = headers
			} else {
				opts["headers"] = map[string]any{"Host": host}
			}
		}
		if earlyData > 0 {
			opts["early-data-header-name"] = "Sec-WebSocket-Protocol"
			opts["max-early-data"] = earlyData
		}
		if httpUpgrade || network == "httpupgrade" {
			opts["v2ray-http-upgrade"] = true
			opts["v2ray-http-upgrade-fast-open"] = true
		}
		node["ws-opts"] = opts
	case "grpc":
		node["network"] = "grpc"
		if serviceName == "" {
			serviceName = strings.TrimPrefix(path, "/")
		}
		opts := map[string]any{"grpc-service-name": serviceName}
		if mode != "" {
			opts["_grpcType"] = mode
		}
		if authority != "" {
			opts["_grpcAuthority"] = authority
		}
		node["grpc-opts"] = opts
	default:
		return fmt.Errorf("unsupported transport: %s", network)
	}
	return nil
}
