// YAML 整数凭据按字段保留原文，锚点在数值字段中仍保持数值语义。
package towerparse

import (
	"encoding/base64"
	"math/big"
	"strconv"
	"strings"

	"go.yaml.in/yaml/v3"
)

func yamlCredentialField(field string) bool {
	switch field {
	case "password", "username", "psk", "auth-str", "token", "private-key-passphrase", "obfs-password":
		return true
	}
	return false
}

// 先记录原文再执行 Schema 转换，避免同一锚点在不同字段下相互污染。
func yamlIntegerSources(n *yaml.Node, out map[*yaml.Node]string) {
	if n.Kind == yaml.ScalarNode && (n.Style == 0 || n.Tag == "!!int" && n.Style&yaml.TaggedStyle != 0) {
		if _, _, ok := mihomoInteger(n.Value); ok {
			out[n] = n.Value
		}
	}
	for _, child := range n.Content {
		yamlIntegerSources(child, out)
	}
}

func preserveYAMLCredentials(n *yaml.Node, sources map[*yaml.Node]string) {
	if n.Kind == yaml.MappingNode {
		for i := 0; i+1 < len(n.Content); i += 2 {
			if !yamlCredentialField(n.Content[i].Value) {
				continue
			}
			value := n.Content[i+1]
			if value.Kind == yaml.AliasNode {
				value = value.Alias
			}
			if source, ok := sources[value]; ok {
				copy := *value
				copy.Tag, copy.Value = "!!str", yamlCredentialInteger(source)
				n.Content[i+1] = &copy
			} else if value.Tag == "!!binary" {
				// js-yaml 返回字节对象，yaml.v3 默认返回字符串；凭据字段必须保留非字符串类型。
				decoded, err := base64.StdEncoding.DecodeString(strings.Map(func(r rune) rune {
					if r == ' ' || r == '\t' || r == '\n' || r == '\r' {
						return -1
					}
					return r
				}, value.Value))
				if err == nil {
					sequence := &yaml.Node{Kind: yaml.SequenceNode, Tag: "!!seq"}
					for _, b := range decoded {
						sequence.Content = append(sequence.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!int", Value: strconv.Itoa(int(b))})
					}
					n.Content[i+1] = sequence
				}
			}
		}
	}
	for _, child := range n.Content {
		preserveYAMLCredentials(child, sources)
	}
}

func yamlCredentialInteger(source string) string {
	unsigned := strings.TrimPrefix(strings.TrimPrefix(source, "+"), "-")
	if !strings.HasPrefix(unsigned, "0x") && !strings.HasPrefix(unsigned, "0o") && !strings.HasPrefix(unsigned, "0b") {
		return source
	}
	value, _ := new(big.Int).SetString(unsigned, 0)
	if strings.HasPrefix(source, "-") {
		return "-" + value.String()
	}
	return value.String()
}
