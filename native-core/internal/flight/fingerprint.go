package flight

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"reflect"
)

// Fingerprint returns the SHA-256 content identity of a Flight. The top-level
// name and underscore-prefixed keys at any level are excluded.
func Fingerprint(value Flight) [32]byte {
	canonical := fingerprintValue(value.Fields)
	if record, ok := canonical.(map[string]any); ok {
		delete(record, "name")
	}
	encoded, err := json.Marshal(canonical)
	if err != nil {
		return sha256.Sum256([]byte(fmt.Sprintf("%v", canonical)))
	}
	return sha256.Sum256(encoded)
}

func fingerprintValue(value any) any {
	if value == nil {
		return nil
	}
	switch v := value.(type) {
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			if len(key) > 0 && key[0] == '_' {
				continue
			}
			out[key] = fingerprintValue(child)
		}
		return out
	case map[any]any:
		out := make(map[string]any, len(v))
		for key, child := range v {
			name := fmt.Sprint(key)
			if len(name) > 0 && name[0] == '_' {
				continue
			}
			out[name] = fingerprintValue(child)
		}
		return out
	case []any:
		out := make([]any, len(v))
		for i, child := range v {
			out[i] = fingerprintValue(child)
		}
		return out
	}

	rv := reflect.ValueOf(value)
	if rv.IsValid() && rv.Kind() == reflect.Slice {
		out := make([]any, rv.Len())
		for i := 0; i < rv.Len(); i++ {
			out[i] = fingerprintValue(rv.Index(i).Interface())
		}
		return out
	}
	return value
}
