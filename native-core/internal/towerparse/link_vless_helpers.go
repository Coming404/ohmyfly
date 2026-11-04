// 移植自 SubBoost vless.ts 与 mihomo/reality.ts、mihomo/vless-encryption.ts @4015347（AGPL-3.0-only）。
// VLESS 链接用到的格式判断与 Reality 字段规范化。
package towerparse

import "strings"

func isRealityPublicKey(value string) bool {
	if len(value) != 43 {
		return false
	}
	for _, char := range value {
		if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') ||
			(char >= '0' && char <= '9') || char == '_' || char == '-' {
			continue
		}
		return false
	}
	return true
}

func normalizeRealityShortID(value string) string {
	value = jsTrimSpace(value)
	if value == "" {
		return ""
	}
	value = strings.TrimPrefix(strings.TrimPrefix(value, "0x"), "0X")
	value = strings.NewReplacer(" ", "", "\t", "", "\r", "", "\n", "", ":", "", "_", "", "-", "").Replace(value)
	if value == "" || len(value) > 16 {
		return ""
	}
	for _, char := range value {
		if !((char >= '0' && char <= '9') || (char >= 'a' && char <= 'f') || (char >= 'A' && char <= 'F')) {
			return ""
		}
	}
	value = strings.ToLower(value)
	if len(value)%2 == 1 {
		value = "0" + value
	}
	return value
}

func isVLESSMLKEMEncryption(value string) bool {
	if !strings.HasPrefix(value, "mlkem768x25519plus.") {
		return false
	}
	parts := strings.Split(value, ".")
	if len(parts) < 4 || (parts[1] != "native" && parts[1] != "xorpub" && parts[1] != "random") ||
		(parts[2] != "1rtt" && parts[2] != "0rtt") {
		return false
	}
	for _, part := range parts[3:] {
		if part == "" {
			return false
		}
		for _, char := range part {
			if (char >= 'A' && char <= 'Z') || (char >= 'a' && char <= 'z') ||
				(char >= '0' && char <= '9') || strings.ContainsRune("+/=_-", char) {
				continue
			}
			return false
		}
	}
	return true
}

// hasStandardVLESSAuthority mirrors upstream hasStandardVlessAuthority.
func hasStandardVLESSAuthority(raw string) bool {
	if index := strings.IndexByte(raw, '#'); index >= 0 {
		raw = raw[:index]
	}
	if index := strings.IndexByte(raw, '?'); index >= 0 {
		raw = raw[:index]
	}
	authority := strings.TrimSuffix(raw, "/")
	atIndex := strings.LastIndexByte(authority, '@')
	if atIndex <= 0 {
		return false
	}
	hostPort := authority[atIndex+1:]
	if hostPort == "" {
		return false
	}
	if strings.HasPrefix(hostPort, "[") {
		bracketEnd := strings.IndexByte(hostPort, ']')
		if bracketEnd < 0 {
			return false
		}
		after := hostPort[bracketEnd+1:]
		return strings.HasPrefix(after, ":") && isLinkDigitString(after[1:])
	}
	colonIndex := strings.LastIndexByte(hostPort, ':')
	return colonIndex > 0 && isLinkDigitString(hostPort[colonIndex+1:])
}

func isLinkDigitString(value string) bool {
	if value == "" {
		return false
	}
	for i := 0; i < len(value); i++ {
		if value[i] < '0' || value[i] > '9' {
			return false
		}
	}
	return true
}
