package flight

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math"
	"reflect"
	"sort"
	"strings"
)

// ContentHash 移植 sublinkPro protocol_hash.go 的字段投影与归一化规则（MIT）。
func ContentHash(value Flight) string {
	encoded, err := json.Marshal(contentFields(value.Fields))
	if err != nil {
		return ""
	}
	hash := sha256.Sum256(encoded)
	return hex.EncodeToString(hash[:])
}

func contentFields(fields map[string]any) map[string]any {
	result := make(map[string]any)
	extra := make(map[string]any)
	for key, value := range fields {
		if strings.HasPrefix(key, "_") {
			continue
		}
		field, known := proxyHashFields[key]
		if known && (field == "Name" || field == "Dialer_proxy" || field == "Udp" || field == "Tfo" || field == "Client_fingerprint") {
			continue
		}
		normalized := normalizeHashValue(reflect.ValueOf(hashScalar(key, value)))
		if normalized == nil {
			continue
		}
		if known {
			result[field] = normalized
		} else {
			extra[key] = normalized
		}
	}
	// 未知属性使用语义树，不引入上游 ClashExtra 的 YAML 行列、注释与节点类型。
	if len(extra) > 0 {
		result["ClashExtra"] = extra
	}
	return result
}

func normalizeHashValue(v reflect.Value) any {
	if !v.IsValid() {
		return nil
	}
	for v.Kind() == reflect.Interface || v.Kind() == reflect.Pointer {
		if v.IsNil() {
			return nil
		}
		v = v.Elem()
	}
	if number, ok := v.Interface().(json.Number); ok {
		if number.String() == "0" {
			return nil
		}
		return number
	}
	switch v.Kind() {
	case reflect.String:
		if v.String() != "" {
			return v.String()
		}
	case reflect.Bool:
		if v.Bool() {
			return true
		}
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		if v.Int() != 0 {
			return v.Int()
		}
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		if v.Uint() != 0 {
			return v.Uint()
		}
	case reflect.Float32, reflect.Float64:
		n := v.Float()
		if n != 0 && !math.IsNaN(n) && !math.IsInf(n, 0) {
			return n
		}
	case reflect.Map:
		result := make(map[string]any)
		for _, key := range v.MapKeys() {
			name := fmt.Sprint(key.Interface())
			if strings.HasPrefix(name, "_") {
				continue
			}
			if value := normalizeHashValue(v.MapIndex(key)); value != nil {
				result[name] = value
			}
		}
		if len(result) > 0 {
			return result
		}
	case reflect.Slice, reflect.Array:
		return normalizeHashSlice(v)
	}
	return nil
}

func normalizeHashSlice(v reflect.Value) any {
	if v.Len() == 0 {
		return nil
	}
	// JSON/YAML 解码的 []any 也识别为字符串数组；整数数组始终保留顺序及零值。
	text := make([]string, v.Len())
	allStrings := true
	for i := range v.Len() {
		s, ok := v.Index(i).Interface().(string)
		if !ok {
			allStrings = false
			break
		}
		text[i] = s
	}
	if allStrings {
		sort.Strings(text)
		return text
	}
	result := make([]any, v.Len())
	for i := range v.Len() {
		child := v.Index(i)
		for child.Kind() == reflect.Interface {
			child = child.Elem()
		}
		if child.IsValid() {
			result[i] = child.Interface()
			if child.Kind() == reflect.Float32 || child.Kind() == reflect.Float64 {
				if n := child.Float(); math.IsNaN(n) || math.IsInf(n, 0) {
					result[i] = nil
				}
			}
		}
		if child.IsValid() && (child.Kind() == reflect.Map || child.Kind() == reflect.Slice || child.Kind() == reflect.Array) {
			result[i] = normalizeHashValue(child)
		}
	}
	return result
}
