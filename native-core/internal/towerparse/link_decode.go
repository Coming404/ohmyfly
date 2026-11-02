// Ported from SubBoost packages/core/src/parser/protocols/url-decode.ts @4015347 (AGPL-3.0-only).
package towerparse

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"
)

func decodeLinkBase64(value string) ([]byte, error) {
	value, err := url.PathUnescape(strings.TrimSpace(value))
	if err != nil {
		return nil, err
	}
	value = strings.Map(func(char rune) rune {
		switch char {
		case ' ', '\t', '\r', '\n':
			return -1
		default:
			return char
		}
	}, value)
	encodings := []*base64.Encoding{
		base64.RawURLEncoding,
		base64.URLEncoding,
		base64.RawStdEncoding,
		base64.StdEncoding,
	}
	for _, encoding := range encodings {
		decoded, err := encoding.DecodeString(value)
		if err == nil {
			return decoded, nil
		}
	}
	return nil, fmt.Errorf("invalid base64 payload")
}

func decodeLinkText(value string) (string, error) {
	decoded, err := decodeLinkBase64(value)
	if err != nil {
		return "", err
	}
	return string(decoded), nil
}

func linkString(query url.Values, keys ...string) string {
	for _, key := range keys {
		if value := strings.TrimSpace(query.Get(key)); value != "" {
			return value
		}
	}
	return ""
}

func linkBool(value string) (bool, bool) {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true, true
	case "0", "false", "no", "off":
		return false, true
	default:
		return false, false
	}
}

func linkBoolValue(query url.Values, keys ...string) (bool, bool) {
	for _, key := range keys {
		if value, exists := query[key]; exists {
			if len(value) == 0 || strings.TrimSpace(value[0]) == "" {
				return true, true
			}
			return linkBool(value[0])
		}
	}
	return false, false
}

func linkList(value string) []string {
	var result []string
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			result = append(result, item)
		}
	}
	return result
}

// linkPort mirrors upstream `parseInt(port || fallback, 10)` followed by the
// NaN / 1..65535 check: leading whitespace and a sign are accepted and parsing
// stops at the first non-digit, so "443abc" is 443 and "0x10" is 0 (invalid).
func linkPort(value string, fallback int) (int, error) {
	if value == "" {
		return fallback, nil
	}
	value = strings.TrimLeftFunc(value, func(char rune) bool {
		return char == '\uFEFF' || (char != '\u0085' && unicode.IsSpace(char))
	})
	negative := false
	if value != "" && (value[0] == '+' || value[0] == '-') {
		negative = value[0] == '-'
		value = value[1:]
	}
	port, digits := 0, 0
	for len(value) > digits {
		digit := strings.IndexByte("0123456789", value[digits])
		if digit == -1 {
			break
		}
		if 65535 >= port {
			port = port*10 + digit
		}
		digits++
	}
	if digits == 0 || negative || port == 0 || port > 65535 {
		return 0, fmt.Errorf("invalid port")
	}
	return port, nil
}

// linkName mirrors upstream `safeDecodeFormUrlEncoded(value) || fallback`.
func linkName(value, fallback string) string {
	if decoded := linkDecodeForm(value); decoded != "" {
		return decoded
	}
	return fallback
}

// linkDecodeComponent mirrors upstream safeDecodeURIComponent: "+" stays,
// and any bad escape or invalid UTF-8 returns the input unchanged.
func linkDecodeComponent(value string) string {
	decoded, ok := linkUnescape(value, true)
	if !ok || !utf8.Valid(decoded) {
		return value
	}
	return string(decoded)
}

// linkDecodeForm mirrors upstream safeDecodeFormUrlEncoded ("+" is a space).
func linkDecodeForm(value string) string {
	if value == "" {
		return ""
	}
	decoded, ok := linkUnescape(strings.ReplaceAll(value, "+", "%20"), true)
	if ok && utf8.Valid(decoded) {
		return string(decoded)
	}
	return linkDecodeComponent(value)
}

// linkSearchParams mirrors `new URLSearchParams(raw)`: "&" separates pairs
// (";" does not), "+" is a space, bad escapes are kept literally and invalid
// UTF-8 becomes U+FFFD. It never fails.
func linkSearchParams(raw string) url.Values {
	values := url.Values{}
	for part := range strings.SplitSeq(strings.TrimPrefix(raw, "?"), "&") {
		if part == "" {
			continue
		}
		key, value, _ := strings.Cut(part, "=")
		values.Add(linkFormComponent(key), linkFormComponent(value))
	}
	return values
}

func linkFormComponent(value string) string {
	decoded, _ := linkUnescape(strings.ReplaceAll(value, "+", " "), false)
	if utf8.Valid(decoded) {
		return string(decoded)
	}
	var builder strings.Builder
	for len(decoded) > 0 {
		char, size := utf8.DecodeRune(decoded)
		builder.WriteRune(char)
		decoded = decoded[size:]
	}
	return builder.String()
}

// linkUnescape decodes %XX escapes. In strict mode a malformed escape fails;
// otherwise it is copied through unchanged.
func linkUnescape(value string, strict bool) ([]byte, bool) {
	decoded := make([]byte, 0, len(value))
	for index := 0; len(value) > index; index++ {
		if value[index] == '%' && len(value) > index+2 {
			high, highOK := linkHexValue(value[index+1])
			low, lowOK := linkHexValue(value[index+2])
			if highOK && lowOK {
				decoded = append(decoded, high*16+low)
				index += 2
				continue
			}
		}
		if value[index] == '%' && strict {
			return nil, false
		}
		decoded = append(decoded, value[index])
	}
	return decoded, true
}

func linkHexValue(char byte) (byte, bool) {
	index := strings.IndexByte("0123456789abcdef0123456789ABCDEF", char)
	if index == -1 {
		return 0, false
	}
	return byte(index % 16), true
}

// linkNeutralURL mirrors upstream parseUrlWithNeutralScheme: authority, path
// and fragment are split by hand, so bad escapes or odd ports never reject
// the link here. Username and Password are already component-decoded.
type linkNeutralURL struct {
	Scheme      string
	Username    string
	Password    string
	HasPassword bool
	Hostname    string
	Port        string
	Path        string
	RawQuery    string
	Fragment    string
}

var linkSchemePattern = regexp.MustCompile(`^([a-zA-Z][a-zA-Z0-9+.-]*)://`)

func linkURL(raw string) (*linkNeutralURL, url.Values, error) {
	match := linkSchemePattern.FindStringSubmatch(raw)
	if match == nil {
		return nil, nil, fmt.Errorf("invalid URL")
	}
	parsed := &linkNeutralURL{Scheme: strings.ToLower(match[1])}
	rest := raw[len(match[0]):]
	rest, parsed.Fragment, _ = strings.Cut(rest, "#")
	rest, parsed.RawQuery, _ = strings.Cut(rest, "?")
	authority := rest
	if index := strings.IndexByte(rest, '/'); index != -1 {
		authority, parsed.Path = rest[:index], rest[index:]
	}
	hostPort := authority
	if userInfo, host, ok := strings.CutLast(authority, "@"); ok {
		hostPort = host
		user, password, hasPassword := strings.Cut(userInfo, ":")
		parsed.Username = linkDecodeComponent(user)
		parsed.Password = linkDecodeComponent(password)
		parsed.HasPassword = hasPassword
	}
	if after, ipv6 := strings.CutPrefix(hostPort, "["); ipv6 {
		host, tail, closed := strings.Cut(after, "]")
		if !closed {
			return nil, nil, fmt.Errorf("invalid URL")
		}
		parsed.Hostname = host
		if port, hasPort := strings.CutPrefix(tail, ":"); hasPort {
			parsed.Port = port
		} else if tail != "" {
			return nil, nil, fmt.Errorf("invalid URL")
		}
	} else if strings.Count(hostPort, ":") == 1 {
		parsed.Hostname, parsed.Port, _ = strings.Cut(hostPort, ":")
	} else {
		parsed.Hostname = hostPort
	}
	return parsed, linkSearchParams(parsed.RawQuery), nil
}

func linkAuthority(parsed *linkNeutralURL, defaultPort int) (string, int, error) {
	server := parsed.Hostname
	if server == "" {
		return "", 0, fmt.Errorf("missing server")
	}
	port, err := linkPort(parsed.Port, defaultPort)
	if err != nil {
		return "", 0, err
	}
	return server, port, nil
}

func addLinkBool(node map[string]any, query url.Values, target string, keys ...string) {
	if value, ok := linkBoolValue(query, keys...); ok {
		node[target] = value
	}
}

func addLinkAlpn(node map[string]any, value string) {
	if values := linkList(value); len(values) > 0 {
		node["alpn"] = values
	}
}

func parseLinkJSON(value string) (map[string]any, error) {
	var object map[string]any
	if err := json.Unmarshal([]byte(value), &object); err != nil {
		return nil, err
	}
	return object, nil
}

func linkHeaderMap(value string) map[string]any {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	var decoded map[string]any
	if json.Unmarshal([]byte(value), &decoded) == nil {
		for key, item := range decoded {
			if _, ok := item.(string); !ok {
				delete(decoded, key)
			}
		}
		if len(decoded) > 0 {
			return decoded
		}
		return nil
	}
	if strings.HasPrefix(value, "{") || strings.HasPrefix(value, "[") {
		return nil
	}
	result := map[string]any{}
	for _, part := range strings.Split(value, "|") {
		key, item, ok := strings.Cut(part, ":")
		if !ok || strings.TrimSpace(key) == "" || strings.TrimSpace(item) == "" {
			continue
		}
		result[strings.TrimSpace(key)] = strings.TrimSpace(item)
	}
	if len(result) == 0 {
		return nil
	}
	return result
}

func splitLinkEarlyData(path, fallback string) (string, int) {
	if path == "" {
		path = fallback
	}
	question := strings.IndexByte(path, '?')
	if question < 0 {
		return path, 0
	}
	base := path[:question]
	if base == "" {
		base = fallback
	}
	for _, part := range strings.Split(path[question+1:], "&") {
		key, value, ok := strings.Cut(part, "=")
		if ok && key == "ed" {
			earlyData, err := strconv.Atoi(value)
			if err == nil && earlyData > 0 {
				return base, earlyData
			}
		}
	}
	return path, 0
}

func linkECHOpts(value string) map[string]any {
	return buildMihomoECHOpts(value)
}
