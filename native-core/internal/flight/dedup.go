package flight

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"reflect"
	"strconv"
	"strings"
)

// DeduplicationConfig 来自 sublinkPro 的塔台与汇总输出规则契约（MIT）。
type DeduplicationConfig struct {
	Mode          string              `json:"mode"`
	CommonFields  []string            `json:"commonFields"`
	ProtocolRules map[string][]string `json:"protocolRules"`
}

func (c DeduplicationConfig) Validate(tower bool) error {
	if c.Mode != "none" && c.Mode != "protocol" && (tower || c.Mode != "common") {
		return errors.New("invalid deduplication mode")
	}
	fields := append([]string{}, c.CommonFields...)
	for _, rule := range c.ProtocolRules {
		fields = append(fields, rule...)
	}
	for _, field := range fields {
		if strings.TrimSpace(field) == "" {
			return errors.New("empty deduplication field")
		}
	}
	return nil
}

// DeduplicationKey 保留上游稳定首个的规则语义；用 JSON 元组消除拼接键歧义。
func DeduplicationKey(value Flight, rule DeduplicationConfig) string {
	fields := rule.CommonFields
	protocol := ""
	switch rule.Mode {
	case "common":
	case "protocol":
		protocol = value.Type
		if protocol == "http" && value.Fields["tls"] == true {
			protocol = "https"
		}
		fields = rule.ProtocolRules[protocol]
	default:
		return ""
	}
	if len(fields) == 0 {
		return ""
	}
	parts := []string{protocol}
	hasValue := false
	for _, field := range fields {
		value := dedupField(value, field, rule.Mode == "common")
		parts = append(parts, field, value)
		hasValue = hasValue || value != ""
	}
	if !hasValue {
		return ""
	}
	encoded, _ := json.Marshal(parts)
	return string(encoded)
}

func dedupField(value Flight, field string, common bool) string {
	if common {
		switch field {
		case "Name", "LinkName", "name":
			return value.Name
		case "Protocol", "type":
			if value.Type == "http" && value.Fields["tls"] == true {
				return "https"
			}
			return value.Type
		case "LinkHost", "server":
			return value.Server
		case "LinkPort", "port":
			return strconv.Itoa(value.Port)
		case "LinkAddress":
			return net.JoinHostPort(value.Server, strconv.Itoa(value.Port))
		}
	}
	if special, ok := protocolSpecialField(value, field); ok {
		return special
	}
	switch field {
	case "Name", "Ps", "Remarks", "Remark", "name":
		return value.Name
	case "Server", "Host", "Hostname", "Add", "Address", "server":
		return value.Server
	case "Port", "port":
		return strconv.Itoa(value.Port)
	case "Type", "type":
		return value.Type
	}
	path := protocolFieldPaths[field]
	if path == "" {
		for key, name := range proxyHashFields {
			if field == name {
				path = key
				break
			}
		}
	}
	if path == "" {
		path = field
	}
	var current any = value.Fields
	for _, part := range strings.Split(path, ".") {
		object, ok := current.(map[string]any)
		if !ok {
			return ""
		}
		current = object[part]
	}
	if current == nil {
		return ""
	}
	switch current.(type) {
	case string, bool, float64, int, int64, json.Number:
		return fmt.Sprint(current)
	default:
		normalized := normalizeHashValue(reflect.ValueOf(current))
		if normalized == nil {
			return ""
		}
		encoded, _ := json.Marshal(normalized)
		return string(encoded)
	}
}

var protocolFieldPaths = map[string]string{
	"Param.Cipher": "cipher", "Param.Password": "password", "Scy": "cipher", "Fp": "client-fingerprint",
	"ClientFingerprint": "client-fingerprint", "Congestion_control": "congestion-controller",
	"Plugin.Name": "plugin", "Plugin.Mode": "plugin-opts.mode", "Plugin.Host": "plugin-opts.host",
	"Plugin.Path": "plugin-opts.path", "Plugin.Tls": "plugin-opts.tls", "Plugin.Mux": "plugin-opts.mux",
	"Plugin.Password": "plugin-opts.password", "Plugin.Version": "plugin-opts.version",
	"Query.Fingerprint": "fingerprint", "Query.Peer": "sni", "Query.Mode": "grpc-opts.grpc-mode",
	"Query.Encryption": "encryption", "Query.PacketEncoding": "packet-encoding",
	"Query.MaxEarlyData": "ws-opts.max-early-data", "Query.EarlyDataHeader": "ws-opts.early-data-header-name",
	"Query.Headers": "ws-opts.headers", "Headers": "ws-opts.headers", "Query.Ech": "ech-opts.config",
	"Query.GrpcUserAgent": "grpc-opts.grpc-user-agent", "Query.PingInterval": "grpc-opts.grpc-ping-interval",
	"Query.MaxConnections": "grpc-opts.grpc-max-connections", "Query.MinStreams": "grpc-opts.grpc-min-streams",
	"Query.MaxStreams": "grpc-opts.grpc-max-streams", "V": "version",
	"Id": "uuid", "ID": "uuid", "UUID": "uuid", "Uuid": "uuid", "Aid": "alterId",
	"Query.Sni": "sni", "Query.ServerName": "servername", "Query.Security": "tls",
	"Query.Type": "network", "Net": "network", "Query.Path": "ws-opts.path", "Path": "ws-opts.path",
	"Query.Host": "ws-opts.headers.Host", "Query.ServiceName": "grpc-opts.grpc-service-name",
	"Query.Pbk": "reality-opts.public-key", "Query.Sid": "reality-opts.short-id",
	"Query.Fp": "client-fingerprint", "Query.Flow": "flow", "Query.Alpn": "alpn",
	"Query.AllowInsecure": "skip-cert-verify", "Method": "cipher", "User": "username",
}
