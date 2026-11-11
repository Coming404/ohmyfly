package flight

import (
	"encoding/json"
	"fmt"
	"strings"
)

// 将分享链接协议对象中的字段语义映射到已解析的 Mihomo 字段，不再导出链接并二次解析。
func protocolSpecialField(value Flight, field string) (string, bool) {
	switch field {
	case "Query.Security", "Tls":
		if value.Type == "vmess" || field == "Query.Security" {
			if lenObject(value.Fields["reality-opts"]) {
				return "reality", true
			}
			if value.Fields["tls"] == true {
				return "tls", true
			}
			return "", true
		}
	case "Host":
		if value.Type == "vmess" {
			return transportField(value, "host"), true
		}
	case "Type":
		if value.Type == "vmess" {
			return transportField(value, "headerType"), true
		}
	case "Path", "Query.Path":
		return transportField(value, "path"), true
	case "Query.Host":
		return transportField(value, "host"), true
	case "Query.AllowInsecure", "Insecure", "Disable_sni":
		key := "skip-cert-verify"
		if field == "Disable_sni" {
			key = "disable-sni"
		}
		if value.Fields[key] == true {
			return "1", true
		}
		return "0", true
	case "Sni", "Query.Sni", "Query.ServerName":
		if s, ok := value.Fields["sni"].(string); ok {
			return s, true
		}
		if s, ok := value.Fields["servername"].(string); ok {
			return s, true
		}
		return "", true
	case "Alpn":
		// VMess 与 TUIC 的分享模型把 ALPN 表示为逗号串，保留其数组的实际内容。
		if v, ok := value.Fields["alpn"].([]any); ok {
			parts := make([]string, len(v))
			for i, item := range v {
				parts[i] = fmt.Sprint(item)
			}
			return strings.Join(parts, ","), true
		}
	}
	return "", false
}

func lenObject(value any) bool { object, ok := value.(map[string]any); return ok && len(object) > 0 }

func transportField(value Flight, field string) string {
	network, _ := value.Fields["network"].(string)
	key := map[string]string{"ws": "ws-opts", "http": "http-opts", "h2": "h2-opts", "grpc": "grpc-opts", "xhttp": "xhttp-opts"}[network]
	object, ok := value.Fields[key].(map[string]any)
	if !ok {
		return ""
	}
	if field == "headerType" {
		if network == "http" {
			return "http"
		}
		return ""
	}
	if field == "host" {
		if network == "ws" || network == "http" {
			headers, ok := object["headers"].(map[string]any)
			if !ok {
				return ""
			}
			encoded, _ := json.Marshal(headers["Host"])
			return string(encoded)
		}
		encoded, _ := json.Marshal(object["host"])
		return string(encoded)
	}
	if field == "path" && network == "grpc" {
		field = "grpc-service-name"
	}
	if current, ok := object[field]; ok {
		encoded, _ := json.Marshal(current)
		return string(encoded)
	}
	return ""
}
