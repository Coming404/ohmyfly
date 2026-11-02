// Ported from SubBoost packages/core/src/parser/protocols/vless.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"net/url"
	"strings"
)

func parseVLESSLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "vless")
	body := strings.TrimPrefix(raw, "vless://")
	// Mirrors upstream normalizeShadowrocketUri: only a non-standard
	// authority followed by a query is decoded as a Shadowrocket payload.
	isShadowrocket := false
	if trimmed := strings.TrimSpace(body); !hasStandardVLESSAuthority(trimmed) {
		if queryIndex := strings.IndexByte(trimmed, '?'); queryIndex >= 0 {
			decoded, decodeErr := decodeLinkText(strings.TrimSpace(trimmed[:queryIndex]))
			if decodeErr != nil {
				return nil, fmt.Errorf("invalid vless payload")
			}
			body = decoded + trimmed[queryIndex:]
			isShadowrocket = true
		}
	}
	parsed, query, err := linkURL("vless://" + body)
	if err != nil {
		return nil, err
	}

	server, port, err := linkAuthority(parsed, 443)
	if err != nil {
		return nil, err
	}
	uuid := parsed.Username
	if parsed.Password != "" {
		combined := uuid + ":" + parsed.Password
		uuid = combined[strings.LastIndexByte(combined, ':')+1:]
	}
	if uuid == "" {
		return nil, fmt.Errorf("missing uuid")
	}

	name := linkName(parsed.Fragment, "")
	if name == "" {
		name = linkName(linkString(query, "remarks", "remark"), linkDefaultName(server, port))
	}
	security := strings.ToLower(linkString(query, "security"))
	publicKey := linkString(query, "pbk", "public-key", "public_key", "publicKey")
	if security == "" && publicKey != "" {
		security = "reality"
	}
	tls := security == "tls" || security == "reality"
	implicitShadowrocketTLS := false
	if value, ok := linkBoolValue(query, "tls"); ok {
		tls = value || tls
		implicitShadowrocketTLS = isShadowrocket && security == "" && publicKey == "" && value
		if implicitShadowrocketTLS {
			security = "tls"
		}
	}
	node := map[string]any{
		"name":   name,
		"type":   "vless",
		"server": server,
		"port":   port,
		"uuid":   uuid,
		"udp":    true,
		"tls":    tls,
	}
	if values, exists := query["ech"]; exists {
		if security != "tls" {
			return nil, fmt.Errorf("ech requires tls")
		}
		value := ""
		if len(values) > 0 {
			value = values[0]
		}
		node["ech-opts"] = linkECHOpts(value)
	}
	if value := linkString(query, "sni", "peer", "servername", "serverName"); value != "" {
		node["servername"] = value
	}
	flow := linkString(query, "flow")
	if flow == "" && isShadowrocket {
		switch linkString(query, "xtls") {
		case "1":
			flow = "xtls-rprx-direct"
		case "2":
			flow = "xtls-rprx-vision"
		}
	}
	if flow != "" {
		node["flow"] = flow
	}
	if value := linkString(query, "encryption", "flow-encryption"); value != "" {
		node["encryption"] = value
	}
	if value := linkString(query, "packet-encoding", "packet_encoding", "packetEncoding"); value != "" {
		node["packet-encoding"] = value
	}
	addLinkAlpn(node, linkString(query, "alpn"))
	if value := linkString(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint"); value != "" {
		node["client-fingerprint"] = value
	} else if implicitShadowrocketTLS {
		node["client-fingerprint"] = "chrome"
	}
	if value, ok := linkBoolValue(query, "allowInsecure", "allow_insecure", "allow-insecure", "insecure"); ok && value {
		node["skip-cert-verify"] = true
	}
	if value, ok := linkBoolValue(query, "tls-verification"); ok && !value {
		node["skip-cert-verify"] = true
	}
	if value, ok := linkBoolValue(query, "tfo", "fast-open", "fast_open", "fastOpen"); ok {
		node["tfo"] = value
	}

	if security == "reality" {
		reality := map[string]any{}
		if publicKey != "" {
			reality["public-key"] = publicKey
		}
		if value := linkString(query, "sid", "short-id", "short_id", "shortId"); value != "" {
			if normalized := normalizeRealityShortID(value); normalized != "" {
				reality["short-id"] = normalized
			}
		}
		if value := linkString(query, "spx", "spider-x"); value != "" {
			reality["_spider-x"] = value
		}
		supportKeys := []string{"support-x25519mlkem768", "support_x25519mlkem768", "supportX25519MLKEM768"}
		var support bool
		var supportOK bool
		for _, key := range supportKeys {
			if _, exists := query[key]; exists {
				if parsed, valid := linkBoolValue(query, key); valid {
					support, supportOK = parsed, true
				}
				break
			}
		}
		if !supportOK && isRealityPublicKey(publicKey) && isVLESSMLKEMEncryption(linkString(query, "encryption", "flow-encryption")) {
			support, supportOK = true, true
		}
		if publicKey != "" && supportOK {
			reality["support-x25519mlkem768"] = support
		}
		if len(reality) > 0 {
			node["reality-opts"] = reality
		}
		if _, exists := node["client-fingerprint"]; !exists {
			node["client-fingerprint"] = "chrome"
		}
	}
	// pcs/hpkp pin the server certificate; mihomo reads it as fingerprint.
	if fingerprint := linkCertificateFingerprint(linkString(query, "pcs"), linkString(query, "hpkp")); fingerprint != "" {
		node["fingerprint"] = fingerprint
	}
	if value := linkString(query, "pqv"); value != "" {
		node["pqv"] = value
	}

	network := linkString(query, "type")
	if network == "" {
		network = linkString(query, "obfs")
	}
	if network == "" {
		if value, ok := linkBoolValue(query, "h2"); ok && value {
			network = "h2"
		}
	}
	if network == "" {
		network = "tcp"
	}
	if strings.EqualFold(network, "none") {
		network = "tcp"
	}
	if strings.EqualFold(network, "tcp") && strings.EqualFold(linkString(query, "headerType", "header_type", "header-type"), "http") {
		network = "http"
	}
	if strings.EqualFold(network, "httpupgrade") {
		network = "ws"
	}
	host := linkString(query, "host", "obfs-host", "obfsParam")
	var xhttpFallbackHeaders map[string]any
	if strings.EqualFold(network, "xhttp") && isShadowrocket {
		if headers := linkHeaderMap(linkString(query, "obfsParam")); headers != nil {
			xhttpFallbackHeaders = headers
			for key, value := range headers {
				if strings.EqualFold(key, "host") {
					host = strings.TrimSpace(fmt.Sprint(value))
					delete(xhttpFallbackHeaders, key)
				}
			}
			if host == linkString(query, "obfsParam") {
				host = ""
			}
		}
	}
	path := linkString(query, "path")
	serviceName := linkString(query, "serviceName", "service_name", "service-name")
	if host == "" && (strings.EqualFold(network, "http") || strings.EqualFold(network, "h2")) {
		if value, ok := node["servername"].(string); ok && value != "" && value != server {
			host = value
		}
	}
	if err := applyVLESSTransport(node, vlessTransportOptions{
		network:     network,
		path:        path,
		host:        host,
		method:      linkString(query, "method"),
		serviceName: serviceName,
		mode:        linkString(query, "mode"),
		authority:   linkString(query, "authority"),
		query:       query,
		httpUpgrade: strings.EqualFold(linkString(query, "type"), "httpupgrade"),
	}); err != nil {
		return nil, err
	}
	if strings.EqualFold(network, "xhttp") {
		if options, ok := node["xhttp-opts"].(map[string]any); ok {
			if explicitHost := linkString(query, "xhttp-host", "xhttp_host", "xhttpHost"); explicitHost == "" && isShadowrocket && host != "" {
				options["host"] = host
			}
		}
	}
	if len(xhttpFallbackHeaders) > 0 {
		if options, ok := node["xhttp-opts"].(map[string]any); ok {
			if explicitHost := linkString(query, "xhttp-host", "xhttp_host", "xhttpHost"); explicitHost == "" && host != "" {
				options["host"] = host
			}
			merged := map[string]any{}
			for key, value := range xhttpFallbackHeaders {
				merged[key] = value
			}
			if headers, ok := options["headers"].(map[string]any); ok {
				for key, value := range headers {
					merged[key] = value
				}
			}
			if len(merged) > 0 {
				options["headers"] = merged
			}
		}
	}
	return node, nil
}

func isRealityPublicKey(value string) bool {
	if len(value) != 43 {
		return false
	}
	for _, char := range value {
		if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') ||
			(char >= '0' && char <= '9') || char == '_' || char == '-' {
			continue
		}
		return false
	}
	return true
}

func normalizeRealityShortID(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	value = strings.TrimPrefix(strings.TrimPrefix(value, "0x"), "0X")
	value = strings.NewReplacer(" ", "", "\t", "", "\r", "", "\n", "", ":", "", "_", "", "-", "").Replace(value)
	if value == "" || len(value) > 16 {
		return ""
	}
	for _, char := range value {
		if !((char >= '0' && char <= '9') || (char >= 'a' && char <= 'f') || (char >= 'A' && char <= 'F')) {
			return ""
		}
	}
	value = strings.ToLower(value)
	if len(value)%2 == 1 {
		value = "0" + value
	}
	return value
}

func isVLESSMLKEMEncryption(value string) bool {
	if !strings.HasPrefix(value, "mlkem768x25519plus.") {
		return false
	}
	parts := strings.Split(value, ".")
	if len(parts) < 4 || (parts[1] != "native" && parts[1] != "xorpub" && parts[1] != "random") ||
		(parts[2] != "1rtt" && parts[2] != "0rtt") {
		return false
	}
	for _, part := range parts[3:] {
		if part == "" {
			return false
		}
		for _, char := range part {
			if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') ||
				(char >= '0' && char <= '9') || strings.ContainsRune("+/=_-", char) {
				continue
			}
			return false
		}
	}
	return true
}

type vlessTransportOptions struct {
	network     string
	path        string
	host        string
	method      string
	serviceName string
	mode        string
	authority   string
	query       url.Values
	httpUpgrade bool
}

func applyVLESSTransport(node map[string]any, options vlessTransportOptions) error {
	network := options.network
	path := options.path
	host := options.host
	method := options.method
	serviceName := options.serviceName
	mode := options.mode
	authority := options.authority
	query := options.query
	httpUpgrade := options.httpUpgrade
	network = strings.ToLower(strings.TrimSpace(network))
	switch network {
	case "", "none", "tcp":
		node["network"] = "tcp"
	case "websocket", "ws":
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
		if httpUpgrade {
			opts["v2ray-http-upgrade"] = true
			opts["v2ray-http-upgrade-fast-open"] = true
		}
		node["ws-opts"] = opts
	case "httpupgrade":
		return applyVLESSTransport(node, vlessTransportOptions{
			network:     "ws",
			path:        path,
			host:        host,
			method:      method,
			serviceName: serviceName,
			mode:        mode,
			authority:   authority,
			query:       query,
			httpUpgrade: true,
		})
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
	case "http":
		node["network"] = "http"
		paths := linkList(path)
		if len(paths) == 0 {
			paths = []string{"/"}
		}
		opts := map[string]any{"method": normalizeHTTPMethod(method), "path": paths}
		if hosts := linkList(host); len(hosts) > 0 {
			opts["headers"] = map[string]any{"Host": hosts}
		}
		node["http-opts"] = opts
	case "h2":
		node["network"] = "h2"
		opts := map[string]any{"path": path}
		if opts["path"] == "" {
			opts["path"] = "/"
		}
		if hosts := linkList(host); len(hosts) > 0 {
			opts["host"] = hosts
		}
		node["h2-opts"] = opts
	case "xhttp":
		return applyXHTTPTransport(node, path, host, query)
	default:
		return fmt.Errorf("unsupported transport: %s", network)
	}
	return nil
}

// hasStandardVLESSAuthority mirrors upstream hasStandardVlessAuthority.
func hasStandardVLESSAuthority(raw string) bool {
	if index := strings.IndexByte(raw, '#'); index >= 0 {
		raw = raw[:index]
	}
	if index := strings.IndexByte(raw, '?'); index >= 0 {
		raw = raw[:index]
	}
	authority := strings.TrimSuffix(raw, "/")
	atIndex := strings.LastIndexByte(authority, '@')
	if atIndex <= 0 {
		return false
	}
	hostPort := authority[atIndex+1:]
	if hostPort == "" {
		return false
	}
	if strings.HasPrefix(hostPort, "[") {
		bracketEnd := strings.IndexByte(hostPort, ']')
		if bracketEnd < 0 {
			return false
		}
		after := hostPort[bracketEnd+1:]
		return strings.HasPrefix(after, ":") && isLinkDigitString(after[1:])
	}
	colonIndex := strings.LastIndexByte(hostPort, ':')
	return colonIndex > 0 && isLinkDigitString(hostPort[colonIndex+1:])
}

func isLinkDigitString(value string) bool {
	if value == "" {
		return false
	}
	for i := 0; i < len(value); i++ {
		if value[i] < '0' || value[i] > '9' {
			return false
		}
	}
	return true
}
