// 移植自 SubBoost packages/core/src/parser/protocols/vless.ts @4015347（AGPL-3.0-only）。
// 支持 vless://uuid@server:port?params#name 与 Shadowrocket 的 vless://BASE64(userinfo@server:port)?params#name。
package towerparse

import (
	"cmp"
	"fmt"
	"net/url"
	"strings"
)

// vlessLink 是解析过程中共享的上下文，对应上游 parseVLESS 里的局部变量。
type vlessLink struct {
	query          url.Values
	shadowrocket   bool
	server         string
	security       string
	sni            string
	implicitSRTLS  bool
	clientFP       string
	publicKey      string
	encryption     string
	transport      string
	transportInput string
}

func parseVLESSLink(raw string) (map[string]any, error) {
	raw = normalizeLinkScheme(raw, "vless")
	body, shadowrocket, err := normalizeShadowrocketVLESS(raw)
	if err != nil {
		return nil, err
	}
	parsed, query, err := linkURL("vless://" + body)
	if err != nil {
		return nil, err
	}

	uuid := parsed.Username
	if shadowrocket {
		if parsed.Password != "" {
			uuid += ":" + parsed.Password
		}
		uuid = uuid[strings.LastIndexByte(uuid, ':')+1:]
	}
	server := parsed.Hostname
	if uuid == "" || server == "" {
		return nil, fmt.Errorf("VLESS 配置缺少必要字段")
	}
	port, err := linkPort(parsed.Port, 443)
	if err != nil {
		return nil, fmt.Errorf("无效的端口号")
	}

	link := &vlessLink{query: query, shadowrocket: shadowrocket, server: server}
	if err := link.resolveTransport(); err != nil {
		return nil, err
	}
	link.resolveSecurity()
	node := map[string]any{
		"name": cmp.Or(linkDecodeForm(parsed.Fragment), linkDecodeForm(pickQueryParam(query, "remarks", "remark")),
			"VLESS 节点"),
		"type":   "vless",
		"server": server,
		"port":   port,
		"uuid":   uuid,
		"udp":    true,
		"tls":    link.security == "tls" || link.security == "reality",
	}
	if err := link.applyTLS(node); err != nil {
		return nil, err
	}
	link.applyReality(node)
	return node, link.applyTransport(node)
}

// normalizeShadowrocketVLESS 对应上游 normalizeShadowrocketUri：只有非标准 authority 且带查询串时，
// 才把 ? 之前的部分当作 Base64 载荷解码。
func normalizeShadowrocketVLESS(raw string) (string, bool, error) {
	body := jsTrimSpace(strings.TrimPrefix(raw, "vless://"))
	if hasStandardVLESSAuthority(body) {
		return strings.TrimPrefix(raw, "vless://"), false, nil
	}
	token, suffix, found := strings.Cut(body, "?")
	if !found {
		return strings.TrimPrefix(raw, "vless://"), false, nil
	}
	decoded, err := decodeLinkText(jsTrimSpace(token))
	if err != nil {
		return "", false, fmt.Errorf("无效的 Base64 编码")
	}
	return decoded + "?" + suffix, true, nil
}

func (link *vlessLink) resolveTransport() error {
	query := link.query
	headerType := strings.ToLower(jsTrimSpace(pickQueryParam(query, "headerType", "header_type", "header-type")))
	h2, _ := linkBool(query.Get("h2"))
	input := query.Get("type")
	if input == "" && h2 {
		input = "h2"
	}
	if input == "" && link.shadowrocket {
		input = strings.ToLower(jsTrimSpace(query.Get("obfs")))
	}
	link.transportInput = jsTrimSpace(cmp.Or(input, "tcp"))
	switch transport := strings.ToLower(link.transportInput); transport {
	case "none":
		link.transport = "tcp"
	case "websocket", "httpupgrade":
		link.transport = "ws"
	case "tcp", "ws", "grpc", "h2", "http", "xhttp":
		link.transport = transport
		if transport == "tcp" && headerType == "http" {
			link.transport = "http"
		}
	default:
		return fmt.Errorf("不支持的 VLESS 传输层: type=%s（仅支持 tcp/ws/http/h2/grpc/xhttp）", cmp.Or(link.transportInput, "(empty)"))
	}
	return nil
}

func (link *vlessLink) resolveSecurity() {
	query := link.query
	link.publicKey = linkString(query, "pbk", "public-key", "public_key", "publicKey")
	link.clientFP = linkString(query, "fp", "fingerprint", "client-fingerprint", "clientFingerprint")
	link.encryption = jsTrimSpace(pickQueryParam(query, "encryption", "flow-encryption"))
	link.sni = pickQueryParam(query, "sni", "peer")
	explicit := strings.ToLower(jsTrimSpace(query.Get("security")))
	tls, _ := linkBool(query.Get("tls"))
	link.implicitSRTLS = explicit == "" && link.publicKey == "" && link.shadowrocket && tls
	switch {
	case explicit != "":
		link.security = explicit
	case link.publicKey != "":
		link.security = "reality"
	case link.implicitSRTLS:
		link.security = "tls"
	default:
		link.security = "none"
	}
}

// applyTLS 写入 ECH、证书校验、SNI、flow、ALPN、指纹等与传输层无关的字段。
func (link *vlessLink) applyTLS(node map[string]any) error {
	query := link.query
	if values, ok := query["ech"]; ok {
		if link.security != "tls" {
			return fmt.Errorf("VLESS 启用 ECH 需要 security=tls（不支持 Reality）")
		}
		node["ech-opts"] = linkECHOpts(jsTrimSpace(values[0]))
	}
	insecure := false
	for _, key := range []string{"allowInsecure", "allow_insecure", "allow-insecure", "insecure"} {
		if value, _ := linkBool(query.Get(key)); value {
			insecure = true
		}
	}
	if value, ok := linkBool(query.Get("tls-verification")); insecure || (ok && !value) {
		node["skip-cert-verify"] = true
	}
	if value, ok := linkBool(linkString(query, "tfo", "fast-open", "fast_open", "fastOpen")); ok {
		node["tfo"] = value
	}
	if link.sni != "" {
		node["servername"] = link.sni
	}
	flow := jsTrimSpace(query.Get("flow"))
	if flow == "" && link.shadowrocket {
		flow = map[string]string{"1": "xtls-rprx-direct", "2": "xtls-rprx-vision"}[query.Get("xtls")]
	}
	for key, value := range map[string]string{
		"flow":            flow,
		"encryption":      link.encryption,
		"packet-encoding": jsTrimSpace(pickQueryParam(query, "packet-encoding", "packet_encoding", "packetEncoding")),
		"pcs":             query.Get("pcs"),
		"pqv":             query.Get("pqv"),
	} {
		if value != "" {
			node[key] = value
		}
	}
	addLinkAlpn(node, query.Get("alpn"))
	if link.clientFP != "" {
		node["client-fingerprint"] = link.clientFP
	} else if link.implicitSRTLS {
		node["client-fingerprint"] = "chrome"
	}
	return nil
}

func (link *vlessLink) applyReality(node map[string]any) {
	if link.security != "reality" {
		return
	}
	query := link.query
	reality := map[string]any{}
	if link.publicKey != "" {
		reality["public-key"] = link.publicKey
	}
	if shortID := normalizeRealityShortID(linkString(query, "sid", "short-id", "short_id", "shortId")); shortID != "" {
		reality["short-id"] = shortID
	}
	// 显式给了支持键时只看它的值，空值或非法值都不输出；没给时由公钥和 MLKEM 加密推断。
	supportKeys := []string{"support-x25519mlkem768", "support_x25519mlkem768", "supportX25519MLKEM768"}
	explicit := false
	for _, key := range supportKeys {
		_, exists := query[key]
		explicit = explicit || exists
	}
	support, supportOK := linkBool(linkString(query, supportKeys...))
	if !explicit {
		support = isRealityPublicKey(link.publicKey) && isVLESSMLKEMEncryption(link.encryption)
		supportOK = support
	}
	if link.publicKey != "" && supportOK {
		reality["support-x25519mlkem768"] = support
	}
	if spiderX := query.Get("spx"); spiderX != "" {
		reality["_spider-x"] = spiderX
	}
	if len(reality) > 0 {
		node["reality-opts"] = reality
	}
	node["client-fingerprint"] = cmp.Or(link.clientFP, "chrome")
}

func (link *vlessLink) applyTransport(node map[string]any) error {
	query := link.query
	path := cmp.Or(query.Get("path"), "/")
	host := cmp.Or(query.Get("host"), query.Get("obfsParam"), query.Get("obfs-host"))
	var frontingSNI []string
	if node["tls"] == true && link.sni != "" && link.sni != link.server {
		frontingSNI = []string{link.sni}
	}

	switch link.transport {
	case "ws":
		node["network"] = "ws"
		wsPath, earlyData := splitLinkEarlyData(path, "/")
		opts := map[string]any{"path": wsPath}
		if headers := link.wsHeaders(host); headers != nil {
			opts["headers"] = headers
		}
		if earlyData > 0 {
			opts["early-data-header-name"] = "Sec-WebSocket-Protocol"
			opts["max-early-data"] = earlyData
		}
		if strings.EqualFold(jsTrimSpace(query.Get("type")), "httpupgrade") {
			opts["v2ray-http-upgrade"] = true
			opts["v2ray-http-upgrade-fast-open"] = true
		}
		node["ws-opts"] = opts
	case "grpc":
		node["network"] = "grpc"
		opts := map[string]any{"grpc-service-name": cmp.Or(pickQueryParam(query, "serviceName", "service_name", "service-name"), strings.TrimPrefix(path, "/"))}
		if mode := query.Get("mode"); mode != "" {
			opts["_grpcType"] = mode
		}
		if authority := query.Get("authority"); authority != "" {
			opts["_grpcAuthority"] = authority
		}
		node["grpc-opts"] = opts
	case "http":
		node["network"] = "http"
		method := strings.ToUpper(cmp.Or(jsTrimSpace(cmp.Or(query.Get("method"), "GET")), "GET"))
		opts := map[string]any{"method": method, "path": hostsOr(path, []string{"/"})}
		if hosts := hostsOr(host, frontingSNI); hosts != nil {
			opts["headers"] = map[string]any{"Host": hosts}
		}
		node["http-opts"] = opts
	case "h2":
		node["network"] = "h2"
		opts := map[string]any{"path": path}
		if hosts := hostsOr(host, frontingSNI); hosts != nil {
			opts["host"] = hosts
		}
		node["h2-opts"] = opts
	case "xhttp":
		return applyXHTTPTransport(node, path, query.Get("obfs-host"), query)
	default:
		node["network"] = "tcp"
	}
	return nil
}

// wsHeaders：Shadowrocket 的 obfsParam 能解析出请求头时优先使用，否则只取 host 的第一项。
func (link *vlessLink) wsHeaders(host string) map[string]any {
	if obfsParam := link.query.Get("obfsParam"); link.shadowrocket && obfsParam != "" {
		if headers := parseShadowrocketHeaderValue(obfsParam); headers != nil {
			return headers
		}
	}
	if hosts := splitList(host); hosts != nil {
		return map[string]any{"Host": hosts[0]}
	}
	return nil
}

// parseShadowrocketHeaderValue：JSON 对象按请求头解析；不是 JSON 对象时整串当作 Host。
func parseShadowrocketHeaderValue(raw string) map[string]any {
	value := jsTrimSpace(raw)
	if value == "" {
		return nil
	}
	if headers := parseJSONStringMap(value); headers != nil {
		return headers
	}
	if _, isObject := parseJSONObject(value); isObject {
		return nil
	}
	return map[string]any{"Host": value}
}

// hostsOr 对应上游 `splitList(host) ?? fallback`。
func hostsOr(host string, fallback []string) []string {
	if hosts := splitList(host); hosts != nil {
		return hosts
	}
	return fallback
}
