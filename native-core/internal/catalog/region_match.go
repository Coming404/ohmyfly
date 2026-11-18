// 名称识别共享中英文目录；地区名称优先于缩写，避免短名称抢占长名称。
package catalog

import (
	"cmp"
	"slices"
	"strings"
	"unicode"
	"unicode/utf8"
)

type regionAlias struct {
	label, code string
	word        bool
	length      int
}

var regionNameAliases = buildRegionAliases()

// buildRegionAliases 统一去重并按最长名称排序，避免每个节点重复计算别名长度。
func buildRegionAliases() []regionAlias {
	aliases := make([]regionAlias, 0, len(regionCatalogRows)*2)
	seen := make(map[string]bool)
	add := func(label, code string) {
		label = strings.ToLower(label)
		if seen[label] {
			return
		}
		seen[label] = true
		aliases = append(aliases, regionAlias{
			label: label, code: code,
			word:   !strings.ContainsFunc(label, func(r rune) bool { return unicode.Is(unicode.Han, r) }),
			length: utf8.RuneCountInString(label),
		})
	}
	for _, row := range regionCatalogRows {
		add(row.English, row.Code)
		add(row.Name, row.Code)
	}
	for _, row := range regionExtraAliases {
		for label := range strings.SplitSeq(row.names, "|") {
			add(label, row.code)
		}
	}
	slices.SortStableFunc(aliases, func(a, b regionAlias) int { return cmp.Compare(b.length, a.length) })
	return aliases
}

// regionFromName 名称及城市别名优先于国旗标记和短代码；输出始终是图标库使用的地区代码。
func regionFromName(name string) string {
	lower := strings.ToLower(strings.Join(strings.Fields(name), " "))
	for _, alias := range regionNameAliases {
		matched := strings.Contains(lower, alias.label)
		if alias.word {
			matched = containsRegionWord(lower, alias.label)
		}
		if matched {
			return alias.code
		}
	}
	if code := regionFromFlag(name); code != "" {
		return code
	}
	// 用户约定 cf 表示 CloudFlare；中非仍以全名或国旗标记识别。
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

// regionFromFlag 仅解读输入中的成对区域指示符，不生成显示用 Emoji，也不接收目录外代码。
func regionFromFlag(name string) string {
	var previous rune
	for _, r := range name {
		if r < 0x1F1E6 || r > 0x1F1FF {
			previous = 0
			continue
		}
		if previous == 0 {
			previous = r
			continue
		}
		code := string([]rune{'A' + previous - 0x1F1E6, 'A' + r - 0x1F1E6})
		for _, row := range regionCatalogRows {
			if row.Code == code {
				return code
			}
		}
		previous = 0
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
