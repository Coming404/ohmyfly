// Ported from SubBoost packages/core/src/parser/protocols/vmess.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

func parseVMessLink(raw string) (map[string]any, bool, error) {
	scheme := "vmess"
	if strings.HasPrefix(strings.ToLower(raw), "vmess1://") {
		scheme = "vmess1"
	}
	raw = normalizeLinkScheme(raw, scheme)
	content := strings.TrimPrefix(raw, scheme+"://")
	hashIndex := strings.IndexByte(content, '#')
	fragment := ""
	if hashIndex >= 0 {
		fragment = content[hashIndex+1:]
		content = content[:hashIndex]
	}
	if scheme == "vmess1" {
		return parseVMessURIConfig(content, fragment, true)
	}

	if strings.Contains(content, "@") || strings.Contains(content, "?") {
		if node, _, err := parseVMessURIConfig(content, fragment, false); err == nil {
			return node, false, nil
		}
	}
	encodedContent := content
	querySuffix := ""
	if index := strings.IndexByte(content, '?'); index >= 0 {
		encodedContent = content[:index]
		querySuffix = content[index:]
	}
	if !strings.Contains(encodedContent, "@") {
		if decodedAuthority, decodeErr := decodeLinkText(encodedContent); decodeErr == nil && strings.Contains(decodedAuthority, "@") {
			if node, _, parseErr := parseVMessURIConfig(decodedAuthority+querySuffix, fragment, false); parseErr == nil {
				return node, true, nil
			}
		}
	}
	decoded, err := decodeLinkText(encodedContent)
	if err != nil {
		return nil, false, err
	}
	config, err := parseLinkJSON(decoded)
	if err != nil {
		if strings.Contains(strings.ToLower(decoded), "= vmess") {
			config, err = parseQuantumultVMessConfig(decoded)
			if err != nil {
				return nil, false, err
			}
		} else {
			return nil, false, fmt.Errorf("invalid vmess json")
		}
	}
	node, err := buildVMessNode(config, fragment)
	return node, true, err
}

func parseQuantumultVMessConfig(decoded string) (map[string]any, error) {
	parts := make([]string, 0)
	for _, part := range strings.Split(decoded, ",") {
		if part = jsTrimSpace(part); part != "" {
			parts = append(parts, part)
		}
	}
	if len(parts) < 5 {
		return nil, fmt.Errorf("invalid quantumult vmess config")
	}
	params := map[string]string{}
	for _, part := range parts {
		if key, value, ok := strings.Cut(part, "="); ok {
			params[jsTrimSpace(key)] = jsTrimSpace(value)
		}
	}
	name := jsTrimSpace(strings.SplitN(parts[0], "=", 2)[0])
	if name == "" {
		name = linkDefaultName(jsTrimSpace(parts[1]), parts[2])
	}
	obfs := strings.ToLower(params["obfs"])
	host := parseObfsHeaderHost(params["obfs-header"])
	if host == "" {
		host = jsTrimSpace(params["obfs-host"])
		if host == "" {
			host = jsTrimSpace(params["obfs_host"])
		}
	}
	path := jsTrimSpace(params["obfs-path"])
	if path == "" {
		path = jsTrimSpace(params["obfs_path"])
	}
	path = stripOuterQuotes(path)
	if decodedPath, decodeErr := url.QueryUnescape(path); decodeErr == nil {
		path = decodedPath
	}
	allowInsecure := params["allowInsecure"]
	if allowInsecure == "" {
		allowInsecure = params["allow_insecure"]
	}
	if strings.EqualFold(params["tls-verification"], "false") {
		allowInsecure = "true"
	}
	network := "tcp"
	if obfs == "ws" || obfs == "wss" {
		network = "ws"
	} else if obfs == "http" {
		network = "http"
	}
	config := map[string]any{
		"ps":            name,
		"add":           jsTrimSpace(parts[1]),
		"port":          jsTrimSpace(parts[2]),
		"scy":           jsTrimSpace(parts[3]),
		"id":            stripOuterQuotes(parts[4]),
		"aid":           firstNonEmptyString(params["aid"], params["alterId"], params["alter_id"], "0"),
		"net":           network,
		"host":          host,
		"path":          path,
		"tls":           obfs == "wss",
		"allowInsecure": allowInsecure,
	}
	return config, nil
}

func firstNonEmptyString(values ...string) string {
	for _, value := range values {
		if jsTrimSpace(value) != "" {
			return jsTrimSpace(value)
		}
	}
	return ""
}

func parseVMessURIConfig(content, fragment string, kitsunebi bool) (map[string]any, bool, error) {
	uri := "vmess://" + content
	parsed, query, err := linkURL(uri)
	if err != nil {
		return nil, false, err
	}
	defaultPort := 80
	if kitsunebi || strings.EqualFold(linkString(query, "security"), "tls") || strings.EqualFold(linkString(query, "tls"), "true") || linkString(query, "tls") == "1" {
		defaultPort = 443
	}
	server, port, err := linkAuthority(parsed, defaultPort)
	if err != nil {
		return nil, false, err
	}
	rawIdentity := parsed.Username
	hasPassword := parsed.HasPassword
	if hasPassword {
		rawIdentity = parsed.Password
	}
	identity := rawIdentity
	network := linkString(query, "network", "net", "type")
	tls := strings.EqualFold(linkString(query, "security"), "tls") || strings.EqualFold(linkString(query, "tls"), "true") || linkString(query, "tls") == "1"
	if hasPassword {
		parts := strings.SplitN(parsed.Username, "+", 2)
		if network == "" || network == "tcp" {
			network = parts[0]
		}
		if len(parts) == 2 && parts[1] != "" {
			tls = true
		}
	}
	if network == "" {
		network = "tcp"
	}
	if hasPassword {
		if dash := strings.LastIndexByte(identity, '-'); dash > 0 {
			if _, parseErr := strconv.Atoi(identity[dash+1:]); parseErr == nil {
				identity = identity[:dash]
			}
		}
	}
	if identity == "" {
		identity = linkString(query, "id", "uuid")
	}
	if !kitsunebi && hasPassword {
		obfs := strings.ToLower(linkString(query, "obfs"))
		switch obfs {
		case "websocket", "wss", "ws":
			network = "ws"
			if obfs == "wss" {
				tls = true
			}
		case "http":
			network = "http"
		}
	}
	if strings.EqualFold(linkString(query, "headerType", "header_type", "header-type"), "http") {
		network = "http"
	}
	path := linkDecodeComponent(parsed.Path)
	if path == "/" {
		path = ""
	}
	if value := linkString(query, "path", "wspath"); value != "" {
		path = value
	}
	if kitsunebi && path == "" {
		path = "/"
	}
	name := linkName(fragment, "")
	if name == "" {
		name = linkName(linkString(query, "remark", "remarks"), linkDefaultName(server, port))
	}
	config := map[string]any{
		"name":    name,
		"type":    "vmess",
		"server":  server,
		"port":    port,
		"uuid":    identity,
		"alterId": intQuery(query, "aid", "alterId", "alter_id"),
		"cipher":  linkString(query, "scy", "cipher", "encryption"),
		"network": network,
	}
	if config["cipher"] == "" {
		config["cipher"] = "auto"
	}
	security := strings.ToLower(linkString(query, "security"))
	if security == "reality" {
		tls = true
	}
	if value, ok := linkBoolValue(query, "tls"); ok {
		tls = value
	}
	config["tls"] = tls
	if value := linkString(query, "sni", "peer"); value != "" {
		config["servername"] = value
	}
	if value := linkString(query, "host"); value != "" {
		config["host"] = value
	}
	config["path"] = path
	if value := linkString(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint"); value != "" {
		config["client-fingerprint"] = value
	}
	if value := linkString(query, "alpn"); value != "" {
		config["alpn"] = value
	}
	if value := linkString(query, "packet-encoding", "packet_encoding", "packetEncoding"); value != "" {
		config["packet-encoding"] = value
	}
	if value, ok := linkBoolValue(query, "authenticated-length", "authenticated_length", "authenticatedLength"); ok {
		config["authenticated-length"] = value
	}
	if value, ok := linkBoolValue(query, "global-padding", "global_padding", "globalPadding"); ok {
		config["global-padding"] = value
	}
	if value := linkString(query, "method"); value != "" {
		config["method"] = value
	}
	addLinkBool(config, query, "skip-cert-verify", "allowInsecure", "allow_insecure", "allow-insecure", "insecure")
	if value, ok := linkBoolValue(query, "tls-verification"); ok && !value {
		config["skip-cert-verify"] = true
	}
	if strings.EqualFold(linkString(query, "headerType", "header_type", "header-type"), "http") {
		config["obfs-type"] = "http"
	}
	if value := linkString(query, "serviceName", "service_name", "service-name"); value != "" {
		config["serviceName"] = value
	}
	if value := linkString(query, "mode"); value != "" {
		config["mode"] = value
	}
	if value := linkString(query, "authority"); value != "" {
		config["authority"] = value
	}
	if values, exists := query["ech"]; exists {
		value := ""
		if len(values) > 0 {
			value = values[0]
		}
		config["ech"] = value
	}
	node, err := buildVMessNode(config, "")
	return node, false, err
}

func buildVMessNode(config map[string]any, fragment string) (map[string]any, error) {
	server := stringField(config, "server", "add", "address")
	port := intField(config, 0, "port")
	uuid := stringField(config, "uuid", "id")
	if server == "" || uuid == "" {
		return nil, fmt.Errorf("missing vmess fields")
	}
	if port < 1 || port > 65535 {
		return nil, fmt.Errorf("invalid vmess port")
	}
	name := linkName(fragment, stringField(config, "ps"))
	if name == "" {
		name = stringField(config, "name")
	}
	if name == "" {
		name = linkDefaultName(server, port)
	}
	node := map[string]any{
		"name":    name,
		"type":    "vmess",
		"server":  server,
		"port":    port,
		"uuid":    uuid,
		"alterId": intField(config, 0, "alterId", "aid", "alter_id"),
		"cipher":  stringField(config, "cipher", "scy", "encryption"),
		"udp":     true,
		"tls":     vmessTLS(config),
	}
	if node["cipher"] == "" {
		node["cipher"] = "auto"
	}
	if value := stringField(config, "servername", "sni", "peer"); value != "" {
		node["servername"] = value
	} else if value := stringField(config, "host"); vmessTLS(config) && value != "" {
		node["servername"] = strings.Split(value, ",")[0]
	}
	if value := stringField(config, "client-fingerprint", "fp", "fingerprint"); value != "" {
		node["client-fingerprint"] = value
	}
	if value, ok := boolField(config, "skip-cert-verify"); ok && value {
		node["skip-cert-verify"] = true
	} else if value, ok := boolField(config, "allowInsecure", "allow_insecure", "allow-insecure", "insecure"); ok && value {
		node["skip-cert-verify"] = true
	}
	if value, exists := config["ech"]; exists {
		if !vmessTLS(config) {
			return nil, fmt.Errorf("ech requires tls")
		}
		node["ech-opts"] = linkECHOpts(fmt.Sprint(value))
	}
	if values, ok := config["alpn"].([]string); ok {
		if len(values) > 0 {
			node["alpn"] = values
		}
	} else if value := stringField(config, "alpn"); value != "" {
		addLinkAlpn(node, value)
	} else if values, ok := config["alpn"].([]any); ok {
		var alpn []string
		for _, item := range values {
			if value := jsTrimSpace(fmt.Sprint(item)); value != "" {
				alpn = append(alpn, value)
			}
		}
		if len(alpn) > 0 {
			node["alpn"] = alpn
		}
	}
	for source, target := range map[string]string{
		"packet-encoding":      "packet-encoding",
		"packetEncoding":       "packet-encoding",
		"authenticated-length": "authenticated-length",
		"authenticatedLength":  "authenticated-length",
		"global-padding":       "global-padding",
		"globalPadding":        "global-padding",
	} {
		if value, ok := boolField(config, source); ok {
			node[target] = value
		} else if value := stringField(config, source); value != "" && target == "packet-encoding" {
			node[target] = value
		}
	}

	network := stringField(config, "network", "net")
	if network == "" {
		network = "tcp"
	}
	if strings.EqualFold(stringField(config, "type"), "http") && strings.EqualFold(network, "tcp") {
		network = "http"
	}
	if stringField(config, "obfs-type") == "http" && strings.EqualFold(network, "tcp") {
		network = "http"
	}
	path := stringField(config, "path")
	host := stringField(config, "host")
	httpHosts := splitList(host)
	ua := ""
	if hasDingTalkHost(httpHosts) {
		ua = dingtalkUserAgent
	}
	if strings.EqualFold(network, "http") {
		httpHeaders := parseHeaderRecord(config["headers"])
		if httpHeaders == nil {
			httpHeaders = map[string]any{}
		}
		if len(httpHosts) > 0 {
			if _, exists := httpHeaders["Host"]; !exists {
				httpHeaders["Host"] = httpHosts
			}
		}
		if edge := stringField(config, "edge"); edge != "" {
			if _, exists := httpHeaders["Edge"]; !exists {
				httpHeaders["Edge"] = []string{edge}
			}
		}
		if ua != "" {
			if _, exists := httpHeaders["User-Agent"]; !exists {
				httpHeaders["User-Agent"] = []string{ua}
			}
		}
		if len(httpHeaders) > 0 {
			node["_http-headers"] = httpHeaders
		}
	}
	if err := applyVMessTransport(node, vmessTransportOptions{
		network:     network,
		path:        path,
		host:        host,
		method:      normalizeHTTPMethod(config["method"]),
		serviceName: stringField(config, "serviceName"),
		mode:        stringField(config, "mode"),
		authority:   stringField(config, "authority"),
		query:       url.Values{},
		httpUpgrade: strings.EqualFold(network, "httpupgrade"),
	}); err != nil {
		return nil, err
	}
	if ua != "" && strings.EqualFold(network, "ws") {
		if opts, ok := node["ws-opts"].(map[string]any); ok {
			headers, _ := opts["headers"].(map[string]any)
			if headers == nil {
				headers = map[string]any{}
			}
			if _, exists := headers["User-Agent"]; !exists {
				headers["User-Agent"] = ua
			}
			opts["headers"] = headers
		}
	}
	if headers, ok := node["_http-headers"]; ok {
		if opts, ok := node["http-opts"].(map[string]any); ok {
			opts["headers"] = headers
		}
		delete(node, "_http-headers")
	}
	return node, nil
}

type vmessTransportOptions struct {
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

func applyVMessTransport(node map[string]any, options vmessTransportOptions) error {
	network := strings.ToLower(jsTrimSpace(options.network))
	path := options.path
	host := options.host
	method := options.method
	serviceName := options.serviceName
	mode := options.mode
	authority := options.authority
	query := options.query
	httpUpgrade := options.httpUpgrade
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
		return applyVMessTransport(node, vmessTransportOptions{
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
		opts := map[string]any{"method": strings.ToUpper(jsTrimSpace(method)), "path": paths}
		if opts["method"] == "" {
			opts["method"] = "GET"
		}
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

func vmessTLS(config map[string]any) bool {
	value := stringField(config, "tls")
	if strings.EqualFold(value, "tls") || strings.EqualFold(value, "true") || value == "1" {
		return true
	}
	if result, ok := boolField(config, "tls"); ok {
		return result
	}
	return strings.EqualFold(stringField(config, "security"), "tls")
}

func intQuery(query url.Values, keys ...string) int {
	value := linkString(query, keys...)
	parsed, err := strconv.Atoi(jsTrimSpace(value))
	if err != nil {
		return 0
	}
	return parsed
}
