package flight

import (
	"fmt"
	"math"
	"net"
	"regexp"
	"strconv"
	"strings"
)

var certificateFingerprint = regexp.MustCompile(`^(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}$|^[0-9a-fA-F]{64}$`)

// Normalize turns a YAML/link node map into a Flight and folds public Mihomo
// aliases without changing case-sensitive credentials or transport values.
func Normalize(input map[string]any) (Flight, error) {
	value := cloneValue(input)
	node := value.(map[string]any)

	typ, ok := nonEmptyString(node["type"])
	if !ok {
		return Flight{}, fmt.Errorf("missing type")
	}
	typ = strings.ToLower(typ)
	node["type"] = typ
	if err := normalizeStringScalars(node); err != nil {
		return Flight{}, err
	}

	name, _ := stringValue(node["name"])
	delete(node, "name")

	server, ok := stringValue(node["server"])
	if !ok || strings.TrimSpace(server) == "" {
		return Flight{}, fmt.Errorf("missing server")
	}
	server = normalizeServer(server)
	node["server"] = server

	port, ok := portValue(node["port"])
	if !ok || port < 1 || port > 65535 {
		return Flight{}, fmt.Errorf("invalid port")
	}
	node["port"] = port

	canonicalizeAliases(node, typ)
	normalizeScalarFields(node)
	normalizeWsPath(node)
	if typ == "ssh" {
		if err := validateSSH(node); err != nil {
			return Flight{}, err
		}
	}

	return Flight{
		Name:   name,
		Type:   typ,
		Server: server,
		Port:   port,
		Fields: node,
	}, nil
}

func cloneValue(value any) any {
	switch v := value.(type) {
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			out[key] = cloneValue(child)
		}
		return out
	case map[any]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			out[fmt.Sprint(key)] = cloneValue(child)
		}
		return out
	case []any:
		out := make([]any, len(v))
		for i, child := range v {
			out[i] = cloneValue(child)
		}
		return out
	default:
		return value
	}
}

func stringValue(value any) (string, bool) {
	switch v := value.(type) {
	case string:
		return v, true
	case fmt.Stringer:
		return v.String(), true
	default:
		return "", false
	}
}

func nonEmptyString(value any) (string, bool) {
	text, ok := stringValue(value)
	return text, ok && strings.TrimSpace(text) != ""
}

func portValue(value any) (int, bool) {
	switch v := value.(type) {
	case int:
		return v, true
	case int8:
		return int(v), true
	case int16:
		return int(v), true
	case int32:
		return int(v), true
	case int64:
		return int(v), true
	case uint:
		return int(v), uint64(v) <= uint64(^uint(0)>>1)
	case uint8:
		return int(v), true
	case uint16:
		return int(v), true
	case uint32:
		return int(v), uint64(v) <= uint64(^uint(0)>>1)
	case uint64:
		return int(v), v <= uint64(^uint(0)>>1)
	case float64:
		return int(v), v == float64(int(v))
	case string:
		parsed, err := strconv.Atoi(strings.TrimSpace(v))
		return parsed, err == nil
	default:
		return 0, false
	}
}

func normalizeServer(value string) string {
	server := strings.TrimSpace(value)
	if strings.HasPrefix(server, "[") && strings.HasSuffix(server, "]") {
		server = server[1 : len(server)-1]
	}
	if ip := net.ParseIP(server); ip != nil {
		return ip.String()
	}
	return strings.TrimSuffix(strings.ToLower(server), ".")
}

func hasValue(value any) bool {
	if value == nil {
		return false
	}
	if text, ok := value.(string); ok {
		return strings.TrimSpace(text) != ""
	}
	return true
}

func getPath(node map[string]any, path ...string) any {
	var current any = node
	for _, key := range path {
		record, ok := current.(map[string]any)
		if !ok {
			return nil
		}
		current = record[key]
	}
	return current
}

func setPath(node map[string]any, value any, path ...string) {
	current := node
	for _, key := range path[:len(path)-1] {
		next, ok := current[key].(map[string]any)
		if !ok {
			next = map[string]any{}
			current[key] = next
		}
		current = next
	}
	current[path[len(path)-1]] = value
}

func deletePath(node map[string]any, path ...string) {
	current := node
	for _, key := range path[:len(path)-1] {
		next, ok := current[key].(map[string]any)
		if !ok {
			return
		}
		current = next
	}
	delete(current, path[len(path)-1])
}

func alias(node map[string]any, canonical []string, aliases ...[]string) {
	if len(canonical) == 0 {
		return
	}
	value := getPath(node, canonical...)
	for _, candidate := range aliases {
		if !hasValue(value) {
			value = getPath(node, candidate...)
		}
	}
	if hasValue(value) {
		setPath(node, value, canonical...)
	}
	for _, candidate := range aliases {
		if strings.Join(candidate, "\x00") != strings.Join(canonical, "\x00") {
			deletePath(node, candidate...)
		}
	}
}

func canonicalizeAliases(node map[string]any, typ string) {
	if typ == "vless" {
		alias(node, []string{"reality-opts", "public-key"},
			[]string{"reality-opts", "pbk"}, []string{"reality-opts", "publicKey"},
			[]string{"reality-opts", "public_key"}, []string{"pbk"}, []string{"public-key"},
			[]string{"publicKey"}, []string{"public_key"})
		alias(node, []string{"reality-opts", "short-id"},
			[]string{"reality-opts", "sid"}, []string{"reality-opts", "shortId"},
			[]string{"reality-opts", "short_id"}, []string{"sid"}, []string{"short-id"},
			[]string{"shortId"}, []string{"short_id"}, []string{"shortid"})
	}
	if typ == "vmess" || typ == "vless" || typ == "trojan" || typ == "anytls" || typ == "trusttunnel" {
		if value := node["fingerprint"]; value != nil && certificateFingerprint.MatchString(strings.TrimSpace(fmt.Sprint(value))) {
			alias(node, []string{"client-fingerprint"}, []string{"fp"}, []string{"clientFingerprint"})
		} else {
			alias(node, []string{"client-fingerprint"}, []string{"fp"}, []string{"fingerprint"}, []string{"clientFingerprint"})
		}
	}
	if typ == "vmess" || typ == "vless" {
		alias(node, []string{"packet-encoding"}, []string{"packetEncoding"}, []string{"packet_encoding"}, []string{"packetencoding"})
	}
	if typ == "vmess" || typ == "vless" || typ == "trojan" {
		alias(node, []string{"grpc-opts", "grpc-service-name"},
			[]string{"grpc-opts", "serviceName"}, []string{"grpc-opts", "service_name"},
			[]string{"grpc-opts", "service-name"}, []string{"serviceName"}, []string{"service_name"},
			[]string{"service-name"}, []string{"servicename"}, []string{"grpc-service-name"})
	}
	if !hasValue(node["skip-cert-verify"]) {
		for _, key := range []string{"allowInsecure", "allow_insecure", "allow-insecure", "allowinsecure", "insecure"} {
			if hasValue(node[key]) {
				node["skip-cert-verify"] = node[key]
				for _, remove := range []string{"allowInsecure", "allow_insecure", "allow-insecure", "allowinsecure", "insecure"} {
					delete(node, remove)
				}
				return
			}
		}
		if value, ok := boolValue(node["tls-verification"]); ok {
			node["skip-cert-verify"] = !value
			delete(node, "tls-verification")
		}
	}
}

func boolValue(value any) (bool, bool) {
	switch v := value.(type) {
	case bool:
		return v, true
	case string:
		switch strings.ToLower(strings.TrimSpace(v)) {
		case "1", "true", "yes", "on":
			return true, true
		case "0", "false", "no", "off":
			return false, true
		}
	}
	return false, false
}

func normalizeStringFields(node map[string]any) {
	for key, value := range node {
		if nested, ok := value.(map[string]any); ok {
			normalizeStringFields(nested)
			continue
		}
		switch key {
		case "password", "username", "psk", "auth-str", "token", "private-key-passphrase", "obfs-password":
			switch scalar := value.(type) {
			case int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64:
				node[key] = fmt.Sprint(scalar)
			}
		}
	}
}

// Upstream keeps YAML numbers as JS Numbers, so these fields accept a string
// or an integer a Number holds exactly; anything else rejects the node.
var mihomoStringScalarFields = []string{
	"password", "username", "psk", "auth-str", "token", "private-key-passphrase", "obfs-password",
}

const maxSafeInteger = 1<<53 - 1

func normalizeStringScalars(node map[string]any) error {
	for _, key := range mihomoStringScalarFields {
		value, ok := node[key]
		if !ok || value == nil {
			continue
		}
		text, ok := mihomoStringScalar(value)
		if !ok {
			return fmt.Errorf("%s must be a string or a safe integer", key)
		}
		node[key] = text
	}
	return nil
}

func mihomoStringScalar(value any) (string, bool) {
	switch v := value.(type) {
	case string:
		return v, true
	case int:
		return safeIntegerText(int64(v))
	case int64:
		return safeIntegerText(v)
	case uint64:
		if v > maxSafeInteger {
			return "", false
		}
		return safeIntegerText(int64(v))
	case float64:
		if v != math.Trunc(v) || math.Abs(v) > maxSafeInteger {
			return "", false
		}
		return safeIntegerText(int64(v))
	}
	return "", false
}

func safeIntegerText(v int64) (string, bool) {
	if v > maxSafeInteger || v < -maxSafeInteger {
		return "", false
	}
	return strconv.FormatInt(v, 10), true
}

func normalizeScalarFields(node map[string]any) {
	normalizeStringFields(node)
	if value := getPath(node, "reality-opts", "short-id"); value != nil {
		if normalized := normalizeShortID(value); normalized != "" {
			setPath(node, normalized, "reality-opts", "short-id")
		} else {
			deletePath(node, "reality-opts", "short-id")
		}
	}
	if value, ok := portValue(node["max-early-data"]); ok {
		node["max-early-data"] = value
	}
}

func normalizeShortID(value any) string {
	text, ok := stringValue(value)
	if !ok {
		if port, valid := portValue(value); valid {
			text = strconv.Itoa(port)
		} else {
			return ""
		}
	}
	text = strings.TrimSpace(text)
	text = strings.TrimPrefix(strings.TrimPrefix(text, "0x"), "0X")
	text = strings.NewReplacer(" ", "", "\t", "", ":", "", "_", "", "-", "").Replace(text)
	if text == "" {
		return ""
	}
	for _, char := range text {
		if !((char >= '0' && char <= '9') || (char >= 'a' && char <= 'f') || (char >= 'A' && char <= 'F')) {
			return ""
		}
	}
	text = strings.ToLower(text)
	if len(text)%2 == 1 {
		text = "0" + text
	}
	if len(text) > 16 {
		return ""
	}
	return text
}

func normalizeWsPath(node map[string]any) {
	if node["network"] != "ws" {
		return
	}
	options, ok := node["ws-opts"].(map[string]any)
	if !ok {
		return
	}
	path, ok := options["path"].(string)
	if !ok || !strings.Contains(path, "?") {
		return
	}
	question := strings.IndexByte(path, '?')
	base := path[:question]
	if base == "" {
		base = "/"
	}
	var kept []string
	early := -1
	for _, part := range strings.Split(path[question+1:], "&") {
		if strings.HasPrefix(part, "ed=") {
			candidate, err := strconv.Atoi(strings.TrimPrefix(part, "ed="))
			if err == nil && candidate > 0 && early < 0 {
				early = candidate
				continue
			}
		}
		if part != "" {
			kept = append(kept, part)
		}
	}
	if early < 0 {
		return
	}
	if len(kept) > 0 {
		base += "?" + strings.Join(kept, "&")
	}
	options["path"] = base
	if _, exists := options["max-early-data"]; !exists {
		options["max-early-data"] = early
	}
	if _, exists := options["early-data-header-name"]; !exists {
		options["early-data-header-name"] = "Sec-WebSocket-Protocol"
	}
}
