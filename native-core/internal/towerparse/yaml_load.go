// 解码后的字段类型遵循上游 Schema；凭据整数通过 yaml.Node 单独保留原文。
package towerparse

import (
	"errors"
	"io"
	"math"
	"math/big"
	"regexp"
	"strconv"
	"strings"

	"go.yaml.in/yaml/v3"
)

// maxYAMLDocumentBytes bounds whole-document loading; larger inputs use the item scanner.
const maxYAMLDocumentBytes = 16 << 20

var (
	shortIDLine        = regexp.MustCompile(`(?m)^(\s*short-id\s*:\s*)([0-9]+)(\s*(?:#.*)?)$`)
	mihomoIntPattern   = regexp.MustCompile(`^(?:[0-9]+|0x[0-9a-fA-F]+|0o[0-7]+|0b[01]+)$`)
	mihomoFloatPattern = regexp.MustCompile(`^(?:[-+]?[0-9]+(?:\.[0-9]*)?(?:[eE][-+]?[0-9]+)?|[-+]?\.[0-9]+(?:[eE][-+]?[0-9]+)?|[-+]?\.(?:inf|Inf|INF)|\.(?:nan|NaN|NAN))$`)

	errMultipleYAMLDocuments = errors.New("expected a single document in the stream, but found more")
)

// loadClashYAML follows upstream parseClashYaml: tabs become two spaces, bare
// numeric short-id values are quoted, and a failed load is retried once after
// the two list-indent repairs. A nil value means no document or a null root.
func loadClashYAML(content string) (any, error) {
	normalized := shortIDLine.ReplaceAllString(strings.ReplaceAll(content, "\t", "  "), `${1}"${2}"${3}`)
	root, err := loadMihomoYAML(normalized)
	if err == nil {
		return root, nil
	}
	return loadMihomoYAML(repairRootFlowProxyListIndent(repairInlineListIndent(normalized)))
}

// loadMihomoYAML follows upstream loadMihomoYaml. Upstream retries the legacy
// flow-indent repair only on js-yaml's "deficient indentation"; yaml.v3 has no
// matching error, so the repair is tried on any load error.
func loadMihomoYAML(content string) (any, error) {
	root, err := decodeSingleYAML(content)
	if err == nil {
		return root, nil
	}
	normalized := normalizeLegacyFlowIndent(content)
	if normalized == content {
		return nil, err
	}
	return decodeSingleYAML(normalized)
}

func decodeSingleYAML(content string) (any, error) {
	decoder := yaml.NewDecoder(strings.NewReader(content))
	var doc yaml.Node
	if err := decoder.Decode(&doc); err != nil {
		if errors.Is(err, io.EOF) {
			return nil, nil
		}
		return nil, err
	}
	var extra yaml.Node
	if err := decoder.Decode(&extra); err == nil {
		return nil, errMultipleYAMLDocuments
	} else if !errors.Is(err, io.EOF) {
		return nil, err
	}
	integerSources := make(map[*yaml.Node]string)
	yamlIntegerSources(&doc, integerSources)
	resolveMihomoScalars(&doc)
	preserveYAMLCredentials(&doc, integerSources)
	// Decode errors (duplicate keys, alias limits) fail the load, as js-yaml does.
	var value any
	if err := doc.Decode(&value); err != nil {
		return nil, err
	}
	return value, nil
}

// resolveMihomoScalars retags plain, untagged scalars with upstream's
// MIHOMO_YAML_SCHEMA rules and turns mapping keys into strings, as JS objects do.
// Aliases share their anchor's node, so they follow the anchor's result.
// 时间戳保留对象类型，避免作为字符串凭据被错误接受。
func resolveMihomoScalars(n *yaml.Node) {
	switch n.Kind {
	case yaml.DocumentNode, yaml.SequenceNode:
		for _, child := range n.Content {
			resolveMihomoScalars(child)
		}
	case yaml.MappingNode:
		for i := 0; i+1 < len(n.Content); i += 2 {
			key := n.Content[i]
			if !(key.Kind == yaml.ScalarNode && key.Style == 0 && key.Value == "<<") {
				resolveMihomoScalars(key)
				stringifyScalarKey(key)
			}
			resolveMihomoScalars(n.Content[i+1])
		}
	case yaml.ScalarNode:
		// 显式整数也使用上游十进制规则；引号中的普通文本保持不变。
		if n.Style == 0 {
			resolvePlainScalar(n)
		} else if n.Tag == "!!int" && n.Style&yaml.TaggedStyle != 0 {
			if tag, text, ok := mihomoInteger(n.Value); ok {
				n.Tag, n.Value = tag, text
			}
		}
	}
}

func stringifyScalarKey(key *yaml.Node) {
	if key.Kind != yaml.ScalarNode {
		return
	}
	if key.Tag == "!!null" {
		key.Value = "null"
	}
	key.Tag = "!!str"
}

func resolvePlainScalar(n *yaml.Node) {
	// 上游保留时间戳对象；不能误变成可接受的字符串凭据。
	if n.Tag == "!!timestamp" {
		return
	}
	switch n.Value {
	case "", "~", "null", "Null", "NULL":
		n.Tag = "!!null"
		return
	case "true", "True", "TRUE":
		n.Tag, n.Value = "!!bool", "true"
		return
	case "false", "False", "FALSE":
		n.Tag, n.Value = "!!bool", "false"
		return
	}
	if tag, text, ok := mihomoInteger(n.Value); ok {
		n.Tag, n.Value = tag, text
		return
	}
	if text, ok := mihomoFloat(n.Value); ok {
		n.Tag, n.Value = "!!float", text
		return
	}
	n.Tag = "!!str"
}

// mihomoInteger reads digits after the sign as decimal, so 0123 is 123.
// Values past int64 become float64; upstream keeps every value as a JS Number,
// which already rounds above 2^53. A non-finite value is not an integer.
func mihomoInteger(source string) (tag, text string, ok bool) {
	unsigned, negative := source, false
	if source != "" && (source[0] == '+' || source[0] == '-') {
		unsigned, negative = source[1:], source[0] == '-'
	}
	if !mihomoIntPattern.MatchString(unsigned) {
		return "", "", false
	}
	base, digits := 10, unsigned
	if len(unsigned) > 2 && unsigned[0] == '0' {
		switch unsigned[1] {
		case 'x':
			base, digits = 16, unsigned[2:]
		case 'o':
			base, digits = 8, unsigned[2:]
		case 'b':
			base, digits = 2, unsigned[2:]
		}
	}
	value, _ := new(big.Int).SetString(digits, base)
	if negative {
		value.Neg(value)
	}
	if value.IsInt64() {
		return "!!int", value.String(), true
	}
	f, _ := new(big.Float).SetInt(value).Float64()
	if math.IsInf(f, 0) {
		return "", "", false
	}
	return "!!float", strconv.FormatFloat(f, 'g', -1, 64), true
}

// mihomoFloat follows js-yaml's core float rule, minus upstream's rejection of
// a sign directly before ".digit". Overflow to infinity stays a string.
func mihomoFloat(source string) (string, bool) {
	if !mihomoFloatPattern.MatchString(source) {
		return "", false
	}
	if len(source) > 2 && (source[0] == '+' || source[0] == '-') && source[1] == '.' && source[2] >= '0' && source[2] <= '9' {
		return "", false
	}
	switch strings.ToLower(source) {
	case ".inf", "+.inf":
		return ".inf", true
	case "-.inf":
		return "-.inf", true
	case ".nan":
		return ".nan", true
	}
	f, _ := strconv.ParseFloat(source, 64)
	if math.IsInf(f, 0) {
		return "", false
	}
	return strconv.FormatFloat(f, 'g', -1, 64), true
}
