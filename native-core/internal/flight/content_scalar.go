package flight

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
)

var hashBandwidthPattern = regexp.MustCompile(`^(\d+)\s*([MGT])bps$`)

func hashScalar(key string, value any) any {
	if value == nil {
		return nil
	}
	if proxyStringFields[key] {
		return fmt.Sprint(value)
	}
	text, ok := value.(string)
	if !ok {
		return value
	}
	if proxyBooleanFields[key] {
		if v, err := strconv.ParseBool(text); err == nil {
			return v
		}
	}
	if proxyIntegerFields[key] {
		if v, err := strconv.ParseInt(strings.TrimSpace(text), 10, 64); err == nil {
			return v
		}
		if key == "up" || key == "down" || key == "up-speed" || key == "down-speed" {
			parts := hashBandwidthPattern.FindStringSubmatch(strings.TrimSpace(text))
			if len(parts) > 0 {
				v, err := strconv.ParseInt(parts[1], 10, 64)
				if err == nil {
					// 上游数值单位为 Mbps；字节速率与无前缀 bps 保留原值，避免误合并。
					factor := int64(1)
					switch parts[2] {
					case "G":
						factor = 1000
					case "T":
						factor = 1000 * 1000
					}
					if v <= (1<<63-1)/factor {
						return v * factor
					}
				}
			}
		}
	}
	// 非标准输入保留原值，避免为了投影而把两个不同配置折叠成空值。
	return value
}
