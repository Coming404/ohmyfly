// 移植自 SubBoost packages/core/src/parser/protocols/vmess.ts @4015347（AGPL-3.0-only）。
// 把 VMess 的各种分享格式（JSON、URI、Quantumult、Shadowrocket、std vmess、Kitsunebi）
// 解析成与上游 VMessConfig 相同键名的配置，再交给 buildVMessNode 生成节点。
package towerparse

import (
	"cmp"
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strings"
)

var vmessQuantumultPattern = regexp.MustCompile(`(?i)=[` + jsSpaceClass + `]*vmess`)

// parseVMessLink 对应上游 parseVMess；第二个返回值标记内容是否经过 Base64 解码。
func parseVMessLink(raw string) (map[string]any, bool, error) {
	scheme := "vmess"
	if strings.HasPrefix(strings.ToLower(raw), "vmess1://") {
		scheme = "vmess1"
	}
	raw = normalizeLinkScheme(raw, scheme)
	if scheme == "vmess1" {
		config, err := parseKitsunebiVMessConfig(raw)
		return buildVMessResult(config, false, err)
	}
	if !strings.HasPrefix(raw, "vmess://") {
		return nil, false, fmt.Errorf("无效的 VMess 链接")
	}

	content := jsTrimSpace(raw[len("vmess://"):])
	switch {
	case looksLikeStandardVMessStyle(content):
		config, err := parseStandardVMessConfig(raw)
		return buildVMessResult(config, false, err)
	case looksLikeShadowrocketStyleVMess(content):
		config, err := parseShadowrocketVMessConfig(raw)
		return buildVMessResult(config, true, err)
	case looksLikeURIStyleVMess(content):
		config, err := parseURIVMessConfig(raw)
		return buildVMessResult(config, false, err)
	}
	config, err := parseEncodedVMessConfig(content)
	return buildVMessResult(config, true, err)
}

func buildVMessResult(config map[string]any, fromBase64 bool, err error) (map[string]any, bool, error) {
	if err != nil {
		return nil, fromBase64, err
	}
	node, err := buildVMessNode(config)
	return node, fromBase64, err
}

// parseEncodedVMessConfig 处理 vmess://base64(JSON) 和 vmess://base64(Quantumult 配置)。
func parseEncodedVMessConfig(content string) (map[string]any, error) {
	encoded, fragment, _ := strings.Cut(content, "#")
	remarks := linkDecodeForm(fragment)
	decoded, err := decodeLinkText(encoded)
	if err != nil {
		return nil, fmt.Errorf("无法解码 VMess 链接")
	}

	var config map[string]any
	var value any
	if json.Unmarshal([]byte(decoded), &value) == nil {
		// 上游 JSON.parse 成功后不再尝试其他格式；非对象在构建节点时因缺字段失败。
		object, ok := value.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("VMess 配置缺少必要字段")
		}
		config = object
	} else if vmessQuantumultPattern.MatchString(decoded) {
		if config, err = parseQuantumultVMessConfig(decoded); err != nil {
			return nil, err
		}
	} else {
		return nil, fmt.Errorf("无效的 VMess JSON 格式")
	}
	if remarks != "" {
		config["ps"] = remarks
	}
	return config, nil
}

func parseShadowrocketVMessConfig(uri string) (map[string]any, error) {
	withoutHash, fragment, _ := strings.Cut(jsTrimSpace(uri[len("vmess://"):]), "#")
	base, rawQuery, found := strings.Cut(withoutHash, "?")
	if !found {
		return nil, fmt.Errorf("无效的 Shadowrocket VMess 链接")
	}
	decoded, err := decodeLinkText(strings.TrimSuffix(base, "/"))
	if err != nil {
		return nil, fmt.Errorf("无效的 Base64 编码")
	}
	match := vmessShadowrocketPattern.FindStringSubmatch(decoded)
	if match == nil {
		return nil, fmt.Errorf("无效的 Shadowrocket VMess 基础信息")
	}

	cipher, uuid, server, port := match[1], match[2], match[3], match[4]
	query := linkSearchParams(rawQuery)
	obfs := strings.ToLower(pickQueryParam(query, "obfs"))
	network := cmp.Or(pickQueryParam(query, "network", "type", "net"), "tcp")
	if obfs == "websocket" {
		network = "ws"
	}
	tlsValue, _ := parseBooleanish(pickQueryParam(query, "tls"))
	path := pickQueryParam(query, "path", "wspath")
	if path == "" && network == "ws" {
		path = "/"
	}
	config := map[string]any{
		"ps": cmp.Or(linkDecodeForm(fragment), linkDecodeForm(pickQueryParam(query, "remarks", "remark")),
			"VMess "+server+":"+port),
		"add":           server,
		"port":          port,
		"id":            uuid,
		"aid":           cmp.Or(pickQueryParam(query, "aid", "alterId", "alter_id"), "0"),
		"scy":           cipher,
		"net":           network,
		"host":          pickQueryParam(query, "host", "obfsParam"),
		"path":          path,
		"sni":           pickQueryParam(query, "sni", "peer"),
		"alpn":          pickQueryParam(query, "alpn"),
		"fp":            pickVMessFingerprint(query),
		"allowInsecure": pickVMessInsecure(query),
	}
	if obfs == "wss" || tlsValue || pickQueryParam(query, "security") == "tls" {
		config["tls"] = "tls"
	}
	setVMessECH(config, query)
	return config, nil
}

// parseStandardVMessConfig 处理 vmess://net(+tls):uuid-aid@server:port/?query#remarks。
func parseStandardVMessConfig(uri string) (map[string]any, error) {
	withoutHash, fragment, _ := strings.Cut(jsTrimSpace(uri[len("vmess://"):]), "#")
	parts, ok := parseStandardVMessParts(withoutHash)
	if !ok {
		return nil, fmt.Errorf("无效的标准 VMess 链接")
	}

	query := linkSearchParams(parts.query)
	network := strings.ToLower(parts.network)
	tlsValue, _ := parseBooleanish(pickQueryParam(query, "tls"))
	host := cmp.Or(pickQueryParam(query, "host", "wsHost", "ws.host"), parseObfsHeaderHost(pickQueryParam(query, "obfsParam")))
	path := pickQueryParam(query, "path", "wspath", "ws-path", "serviceName", "service_name")
	headerType := ""
	switch network {
	case "tcp", "kcp":
		headerType = pickQueryParam(query, "type")
	case "quic":
		headerType = pickQueryParam(query, "security")
		host = pickQueryParam(query, "type")
		path = pickQueryParam(query, "key")
	}
	config := map[string]any{
		"ps":            cmp.Or(linkDecodeForm(fragment), parts.server+":"+parts.port),
		"add":           parts.server,
		"port":          parts.port,
		"id":            parts.uuid,
		"aid":           parts.aid,
		"scy":           cmp.Or(pickQueryParam(query, "scy", "cipher", "encryption"), "auto"),
		"net":           network,
		"type":          headerType,
		"host":          host,
		"path":          path,
		"sni":           pickQueryParam(query, "sni", "peer"),
		"alpn":          pickQueryParam(query, "alpn"),
		"fp":            pickVMessFingerprint(query),
		"allowInsecure": pickVMessInsecure(query),
		"mode":          pickQueryParam(query, "mode"),
		"authority":     pickQueryParam(query, "authority"),
	}
	if jsTrimSpace(parts.tlsTag) != "" || tlsValue {
		config["tls"] = "tls"
	}
	setVMessECH(config, query)
	return config, nil
}

type vmessStandardParts struct {
	network, tlsTag, uuid, aid, server, port, query string
}

func parseStandardVMessParts(value string) (vmessStandardParts, bool) {
	var parts vmessStandardParts
	base, query, _ := strings.Cut(value, "?")
	parts.query = query
	base = strings.TrimSuffix(base, "/")
	schemeIndex := strings.IndexByte(base, ':')
	if schemeIndex <= 0 {
		return parts, false
	}
	parts.network, parts.tlsTag, _ = strings.Cut(base[:schemeIndex], "+")
	if !isAlphaString(parts.network) || (parts.tlsTag != "" && !isAlphaString(parts.tlsTag)) {
		return parts, false
	}

	rest := base[schemeIndex+1:]
	atIndex := strings.IndexByte(rest, '@')
	if atIndex <= 0 {
		return parts, false
	}
	identity, hostPort := rest[:atIndex], rest[atIndex+1:]
	dashIndex := strings.LastIndexByte(identity, '-')
	if dashIndex <= 0 {
		return parts, false
	}
	parts.uuid, parts.aid = identity[:dashIndex], identity[dashIndex+1:]
	if strings.ContainsFunc(parts.uuid, isJSSpace) || !isDigitString(parts.aid) {
		return parts, false
	}
	colonIndex := strings.LastIndexByte(hostPort, ':')
	if colonIndex <= 0 {
		return parts, false
	}
	parts.server, parts.port = hostPort[:colonIndex], hostPort[colonIndex+1:]
	return parts, isDigitString(parts.port)
}

// parseKitsunebiVMessConfig 处理 vmess1://uuid@server:port/path?network=...#remarks。
func parseKitsunebiVMessConfig(uri string) (map[string]any, error) {
	withoutHash, fragment, _ := strings.Cut(jsTrimSpace(uri[len("vmess1://"):]), "#")
	base, addition, _ := strings.Cut(withoutHash, "?")
	uuid, hostPort, found := strings.Cut(base, "@")
	if !found || uuid == "" {
		return nil, fmt.Errorf("无效的 Kitsunebi VMess 链接")
	}
	server, port, found := strings.Cut(hostPort, ":")
	if !found || server == "" {
		return nil, fmt.Errorf("无效的 Kitsunebi VMess 链接")
	}
	path := ""
	if slashIndex := strings.IndexByte(port, '/'); slashIndex != -1 {
		port, path = port[:slashIndex], port[slashIndex:]
	}

	query := linkSearchParams(addition)
	config := map[string]any{
		"ps":   cmp.Or(linkDecodeForm(fragment), server+":"+port),
		"add":  server,
		"port": port,
		"id":   uuid,
		"aid":  "0",
		"scy":  "auto",
		"net":  cmp.Or(pickQueryParam(query, "network", "type", "net"), "tcp"),
		"host": pickQueryParam(query, "ws.host", "host"),
		"path": cmp.Or(path, pickQueryParam(query, "path", "wspath")),
		"sni":  pickQueryParam(query, "sni", "peer"),
	}
	if enabled, _ := parseBooleanish(pickQueryParam(query, "tls")); enabled {
		config["tls"] = "tls"
	}
	setVMessECH(config, query)
	return config, nil
}

func parseQuantumultVMessConfig(decoded string) (map[string]any, error) {
	parts := linkList(decoded)
	if len(parts) < 5 {
		return nil, fmt.Errorf("无效的 Quantumult VMess 配置")
	}
	params := map[string]string{}
	for _, part := range parts {
		if key, value, ok := strings.Cut(part, "="); ok {
			params[jsTrimSpace(key)] = jsTrimSpace(value)
		}
	}

	obfs := strings.ToLower(params["obfs"])
	obfsHeader := cmp.Or(params["obfs-header"], params["obfs_header"])
	path := cmp.Or(params["obfs-path"], params["obfs_path"])
	if path != "" {
		path = linkDecodeForm(stripOuterQuotes(path))
	}
	allowInsecure := cmp.Or(params["allowInsecure"], params["allow_insecure"])
	if allowInsecure == "" && params["tls-verification"] == "false" {
		allowInsecure = "true"
	}
	network := "tcp"
	switch obfs {
	case "ws", "wss":
		network = "ws"
	case "http":
		network = "http"
	}
	config := map[string]any{
		"ps":            cmp.Or(jsTrimSpace(strings.Split(parts[0], "=")[0]), "VMess 节点"),
		"add":           parts[1],
		"port":          parts[2],
		"scy":           parts[3],
		"id":            stripOuterQuotes(parts[4]),
		"aid":           cmp.Or(params["aid"], params["alterId"], params["alter_id"], "0"),
		"net":           network,
		"host":          cmp.Or(parseObfsHeaderHost(obfsHeader), params["obfs-host"], params["obfs_host"]),
		"path":          path,
		"allowInsecure": allowInsecure,
	}
	if obfs == "wss" {
		config["tls"] = "tls"
	}
	return config, nil
}

// parseURIVMessConfig 处理 vmess://uuid@server:port?type=...#name。
func parseURIVMessConfig(uri string) (map[string]any, error) {
	parsed, query, err := linkURL(uri)
	if err != nil {
		return nil, err
	}
	network := cmp.Or(strings.ToLower(pickString(cmp.Or(query.Get("type"), query.Get("net")))), "tcp")
	if network == "none" {
		network = "tcp"
	}
	headerType := strings.ToLower(pickString(pickQueryParam(query, "headerType", "header_type", "header-type")))
	security := strings.ToLower(pickString(query.Get("security")))
	tlsValue, _ := parseBooleanish(query.Get("tls"))
	tls := security == "tls" || security == "reality" || tlsValue
	serviceName := pickQueryParam(query, "serviceName", "service_name", "service-name")
	path := cmp.Or(query.Get("path"), "/")
	if network == "grpc" {
		path = cmp.Or(serviceName, strings.TrimPrefix(path, "/"))
	}
	defaultPort := "80"
	if tls {
		defaultPort = "443"
	}

	config := map[string]any{
		"ps": cmp.Or(linkDecodeForm(parsed.Fragment), linkDecodeForm(pickQueryParam(query, "remark", "remarks")),
			"VMess 节点"),
		"add":                 parsed.Hostname,
		"port":                cmp.Or(parsed.Port, defaultPort),
		"id":                  cmp.Or(parsed.Username, pickString(query.Get("id"))),
		"aid":                 cmp.Or(pickQueryParam(query, "aid", "alterId", "alter_id"), "0"),
		"scy":                 cmp.Or(pickQueryParam(query, "encryption", "scy", "cipher"), "auto"),
		"net":                 network,
		"host":                query.Get("host"),
		"edge":                query.Get("edge"),
		"path":                path,
		"method":              query.Get("method"),
		"sni":                 pickQueryParam(query, "sni", "peer"),
		"alpn":                query.Get("alpn"),
		"fp":                  pickVMessFingerprint(query),
		"allowInsecure":       pickVMessInsecure(query),
		"serviceName":         serviceName,
		"packetEncoding":      pickQueryParam(query, "packet-encoding", "packet_encoding", "packetEncoding"),
		"authenticatedLength": pickQueryParam(query, "authenticated-length", "authenticated_length", "authenticatedLength"),
		"globalPadding":       pickQueryParam(query, "global-padding", "global_padding", "globalPadding"),
	}
	if headerType == "http" {
		config["net"], config["type"] = "tcp", "http"
	}
	if tls {
		config["tls"] = "tls"
	}
	setVMessECH(config, query)
	return config, nil
}

func pickVMessFingerprint(query url.Values) string {
	return pickQueryParam(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint")
}

func pickVMessInsecure(query url.Values) string {
	return pickQueryParam(query, "allowInsecure", "allow_insecure", "allow-insecure", "insecure")
}

// setVMessECH 只要查询里出现 ech 键（值可为空）就写入，上游据此要求 TLS 并生成 ech-opts。
func setVMessECH(config map[string]any, query url.Values) {
	if values, ok := query["ech"]; ok {
		config["ech"] = values[0]
	}
}
