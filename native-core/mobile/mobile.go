package mobile

import (
	"encoding/json"
	"fmt"
	"math"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"github.com/Coming404/ohmyfly/native-core/internal/towerparse"
)

// shortcut: 版本暂以桥接 API 常量返回，发布流程接入后再改为构建注入。
const version = "0.1.0"

// Version 返回 mobile 桥接 API 的版本。
func Version() string {
	return version
}

// ParseSubscription 解析订阅原文并返回 JSON 结果。
// 失败时返回 {"error":"..."}。这里不用 error 返回值：gomobile 会把它导出为
// NSError** 参数，Kotlin/Native cinterop 无法导入这类声明。
func ParseSubscription(content string) string {
	encoded, err := parseSubscription(content)
	if err != nil {
		return encodeError(err)
	}
	return encoded
}

type errorResult struct {
	Error string `json:"error"`
}

func encodeError(err error) string {
	encoded, marshalErr := json.Marshal(errorResult{Error: err.Error()})
	if marshalErr != nil {
		return `{"error":"marshal error result"}`
	}
	return string(encoded)
}

func parseSubscription(content string) (string, error) {
	result := subscriptionResult{
		Nodes: make([]subscriptionNode, 0),
	}
	stats, err := towerparse.Parse(strings.NewReader(content), func(value flight.Flight) error {
		result.Nodes = append(result.Nodes, subscriptionNode{
			Name:         value.Name,
			Type:         value.Type,
			Server:       value.Server,
			Port:         value.Port,
			Fields:       jsonSafeValue(value.Fields).(map[string]any),
			SourceFormat: value.SourceFormat,
			FromBase64:   value.FromBase64,
		})
		return nil
	})
	if err != nil {
		return "", fmt.Errorf("parse subscription: %w", err)
	}

	result.Candidates = stats.Candidates
	result.Parsed = stats.Parsed
	result.Skipped = stats.Skipped
	result.YAML = stats.YAML
	result.Links = stats.Links
	result.Base64 = stats.Base64
	result.Base64Layers = stats.Base64Layers
	result.FormatCounts = stats.FormatCounts
	result.SkippedByReason = stats.SkippedByReason

	encoded, err := json.Marshal(result)
	if err != nil {
		return "", fmt.Errorf("marshal subscription result: %w", err)
	}
	return string(encoded), nil
}

func jsonSafeValue(value any) any {
	switch v := value.(type) {
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			out[key] = jsonSafeValue(child)
		}
		return out
	case map[any]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			out[fmt.Sprint(key)] = jsonSafeValue(child)
		}
		return out
	case []any:
		out := make([]any, len(v))
		for index, child := range v {
			out[index] = jsonSafeValue(child)
		}
		return out
	case float64:
		if math.IsNaN(v) || math.IsInf(v, 0) {
			return nil
		}
		return v
	case float32:
		if math.IsNaN(float64(v)) || math.IsInf(float64(v), 0) {
			return nil
		}
		return v
	default:
		return value
	}
}

type subscriptionResult struct {
	Nodes           []subscriptionNode `json:"nodes"`
	Candidates      int                `json:"candidates"`
	Parsed          int                `json:"parsed"`
	Skipped         int                `json:"skipped"`
	YAML            int                `json:"yaml"`
	Links           int                `json:"links"`
	Base64          int                `json:"base64"`
	Base64Layers    int                `json:"base64Layers"`
	FormatCounts    map[string]int     `json:"formatCounts"`
	SkippedByReason map[string]int     `json:"skippedByReason"`
}

type subscriptionNode struct {
	Name         string         `json:"name"`
	Type         string         `json:"type"`
	Server       string         `json:"server"`
	Port         int            `json:"port"`
	Fields       map[string]any `json:"fields"`
	SourceFormat string         `json:"sourceFormat"`
	FromBase64   bool           `json:"fromBase64"`
}
