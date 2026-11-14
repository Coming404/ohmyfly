// 名称识别共享中英文目录；地区名称优先于缩写，避免短名称抢占长名称。
package catalog

import (
	"strings"
	"unicode/utf8"
)

type regionAlias struct {
	label, code string
	chinese     bool
}

var regionNameAliases = buildRegionAliases()

// buildRegionAliases 预先规范化名称，不在逐节点识别时重复转换整个目录。
func buildRegionAliases() []regionAlias {
	aliases := make([]regionAlias, 0, len(regionCatalogRows)*2)
	for _, row := range regionCatalogRows {
		aliases = append(aliases, regionAlias{strings.ToLower(row.English), row.Code, false}, regionAlias{row.Name, row.Code, true})
	}
	for _, row := range []struct{ label, code string }{
		{"Hong Kong", "HK"}, {"Macau", "MO"}, {"Macao", "MO"},
		{"Myanmar", "MM"}, {"Burma", "MM"}, {"North Macedonia", "MK"},
		{"Eswatini", "SZ"}, {"Czechia", "CZ"}, {"Türkiye", "TR"},
	} {
		aliases = append(aliases, regionAlias{strings.ToLower(row.label), row.code, false})
	}
	return aliases
}

// regionFromName 对英文全名和代码忽略大小写，中文直接匹配；最长全名先于两字母代码。
func regionFromName(name string) string {
	lower := strings.ToLower(strings.Join(strings.Fields(name), " "))
	best, length := "", 0
	for _, alias := range regionNameAliases {
		size := utf8.RuneCountInString(alias.label)
		if size <= length {
			continue
		}
		matched := strings.Contains(lower, alias.label)
		if !alias.chinese {
			matched = containsRegionWord(lower, alias.label)
		}
		if matched {
			best, length = alias.code, size
		}
	}
	if best != "" {
		return best
	}
	// 用户约定 cf 表示 CloudFlare；中非仍以中文或英文全名识别。
	if containsRegionWord(lower, "cf") {
		return "cloudflare"
	}
	for _, row := range regionCatalogRows {
		if row.Code != "CF" && containsRegionWord(lower, strings.ToLower(row.Code)) {
			return row.Code
		}
	}
	return ""
}

// containsRegionWord 阻止代码落入英文单词内部，同时允许 HK01、香港HK 等节点命名。
func containsRegionWord(name, word string) bool {
	for start := 0; start < len(name); {
		relative := strings.Index(name[start:], word)
		if relative < 0 {
			return false
		}
		at := start + relative
		end := at + len(word)
		if (at == 0 || !asciiLetter(name[at-1])) && (end == len(name) || !asciiLetter(name[end])) {
			return true
		}
		start = at + 1
	}
	return false
}

// asciiLetter 只把拉丁字母视为代码边界约束，数字和中文允许紧邻地区缩写。
func asciiLetter(value byte) bool { return value >= 'a' && value <= 'z' }
