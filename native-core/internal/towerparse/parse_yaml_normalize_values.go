// 移植自 SubBoost clash-yaml.ts、port-spec.ts @4015347（AGPL-3.0-only）。
// 仅补 YAML 所需的 JavaScript 数字、字符串及对象展开语义。
package towerparse

import (
	"maps"
	"math"
	"math/big"
	"regexp"
	"strconv"
	"strings"
)

var yamlJSDecimal = regexp.MustCompile(`^[+-]?(?:[0-9]+\.?[0-9]*|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$`)

func yamlJSString(value any) string {
	switch v := value.(type) {
	case nil:
		return ""
	case string:
		return v
	case bool:
		return strconv.FormatBool(v)
	case int:
		return yamlJSString(float64(v))
	case int64:
		return yamlJSString(float64(v))
	case uint64:
		return yamlJSString(float64(v))
	case float64:
		if math.IsNaN(v) {
			return "NaN"
		}
		if math.IsInf(v, 1) {
			return "Infinity"
		}
		if math.IsInf(v, -1) {
			return "-Infinity"
		}
		return jsNumberString(v)
	case []any:
		parts := make([]string, len(v))
		for i, item := range v {
			parts[i] = yamlJSString(item)
		}
		return strings.Join(parts, ",")
	default:
		return "[object Object]"
	}
}

// yamlJSNumber 对应 VMess 的 Number(value) || 0，而不是整数解析。
func yamlJSNumber(value any) float64 {
	if v, ok := value.(bool); ok {
		if v {
			return 1
		}
		return 0
	}
	text := jsTrimSpace(yamlJSString(value))
	switch text {
	case "Infinity", "+Infinity":
		return math.Inf(1)
	case "-Infinity":
		return math.Inf(-1)
	}
	if len(text) > 2 && text[0] == '0' {
		base := 0
		switch text[1] {
		case 'x', 'X':
			base = 16
		case 'b', 'B':
			base = 2
		case 'o', 'O':
			base = 8
		}
		if base != 0 {
			integer, ok := new(big.Int).SetString(text[2:], base)
			if !ok || strings.ContainsAny(text[2:], "+-_") {
				return 0
			}
			result, _ := integer.Float64()
			return result
		}
	}
	if !yamlJSDecimal.MatchString(text) {
		return 0
	}
	parsed, _ := strconv.ParseFloat(text, 64)
	return parsed
}

func setYAMLPort(record map[string]any) {
	value := record["port"]
	delete(record, "port")
	switch value.(type) {
	case string, int, int64, uint64, float64:
		text := jsTrimSpace(yamlJSString(value))
		if !isDigitString(text) {
			return
		}
		port, err := strconv.Atoi(text)
		if err == nil && port >= 1 && port <= 65535 {
			record["port"] = port
		}
	}
}

func yamlPortsSpec(value any) string {
	switch v := value.(type) {
	case string, int, int64, uint64:
	case float64:
		if math.IsInf(v, 0) || math.IsNaN(v) || v != math.Trunc(v) {
			return ""
		}
	default:
		return ""
	}
	return strings.NewReplacer(";", ",", "/", ",").Replace(removeJSSpace(yamlJSString(value)))
}

func yamlObjectSpread(value any) (map[string]any, bool) {
	if record, ok := yamlMapping(value); ok {
		return maps.Clone(record), true
	}
	if values, ok := value.([]any); ok {
		record := make(map[string]any, len(values))
		for i, value := range values {
			record[strconv.Itoa(i)] = value
		}
		return record, true
	}
	return nil, false
}

// YAML 对象不能提供 JS 可调用的 toString；覆盖该属性会使强制转换抛错。
func yamlJSCoercible(value any) bool {
	if record, ok := yamlMapping(value); ok {
		_, shadowed := record["toString"]
		return !shadowed
	}
	if values, ok := value.([]any); ok {
		for _, value := range values {
			if !yamlJSCoercible(value) {
				return false
			}
		}
	}
	return true
}
