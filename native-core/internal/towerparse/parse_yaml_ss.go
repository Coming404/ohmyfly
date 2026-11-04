// Ported from SubBoost packages/core/src/parser/clash-yaml.ts @4015347 (AGPL-3.0-only).
package towerparse

import "strings"

// normalizeYAMLShadowsocks 对应上游 normalizeNode 的 ss/ssr 分支：
// 插件名和插件选项按链接解析的规则规范化，缺省的加密方式、协议和混淆补成上游默认值。
// record 是 YAML 解码出的临时 map，直接原地修改。
func normalizeYAMLShadowsocks(record map[string]any) {
	typ, _ := record["type"].(string)
	switch strings.ToLower(jsTrimSpace(typ)) {
	case "ss":
		defaultYAMLString(record, "cipher", "aes-256-gcm")
		normalizeYAMLSSPlugin(record)
	case "ssr":
		defaultYAMLString(record, "cipher", "aes-256-cfb")
		defaultYAMLString(record, "protocol", "origin")
		defaultYAMLString(record, "obfs", "plain")
	}
}

func normalizeYAMLSSPlugin(record map[string]any) {
	plugin, _ := record["plugin"].(string)
	rawOptions, hasOptions := record["plugin-opts"]
	options, isMap := yamlMapping(rawOptions)
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
