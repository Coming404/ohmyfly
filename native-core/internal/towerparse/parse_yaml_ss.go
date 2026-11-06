// 移植自 SubBoost parser/clash-yaml.ts @4015347（AGPL-3.0-only）。
// SS 插件选项共用链接解析的纯规范化函数。
package towerparse

import "strings"

func normalizeYAMLSSPlugin(record map[string]any) {
	plugin, _ := record["plugin"].(string)
	rawOptions, hasOptions := record["plugin-opts"]
	options, isMap := yamlMapping(rawOptions)
	// 已知非 obfs 插件会展开数组；未知插件仍透传原始数组。
	name := strings.ToLower(jsTrimSpace(plugin))
	if name == "v2ray-plugin" || name == "xray-plugin" || name == "gost-plugin" {
		options, isMap = yamlObjectSpread(rawOptions)
	}
	plugin, options = normalizeSSPlugin(plugin, options)
	delete(record, "plugin")
	if plugin == "" {
		delete(record, "plugin-opts")
		return
	}
	record["plugin"] = plugin
	// 与上游一致：plugin-opts 不是映射时，obfs 丢弃该值，其他插件原样保留。
	if hasOptions && !isMap && plugin != "obfs" {
		return
	}
	delete(record, "plugin-opts")
	if options != nil {
		record["plugin-opts"] = options
	}
}

// defaultYAMLString 对应上游 `(proxy[key] as string) || fallback`：只有 JS 假值才替换。
func defaultYAMLString(record map[string]any, key, fallback string) {
	if isFalsyYAML(record[key]) {
		record[key] = fallback
	}
}
