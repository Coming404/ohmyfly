package towerparse

import (
	"math"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"
)

// isJSSpace reports whether char is stripped by JavaScript
// String.prototype.trim and matched by the regexp class \s (ECMAScript
// WhiteSpace plus LineTerminator). That set equals unicode.IsSpace without
// U+0085 and with U+FEFF added.
func isJSSpace(char rune) bool {
	return char == '\uFEFF' || (char != '\u0085' && unicode.IsSpace(char))
}

// jsNumberString 对应 JavaScript String(number)：绝对值在 [1e-6, 1e21) 内用定点写法，
// 其余用指数写法且指数不补零（1e-7、1e+21）。
func jsNumberString(value float64) string {
	if value == 0 {
		return "0"
	}
	if abs := math.Abs(value); abs >= 1e-6 && abs < 1e21 {
		return strconv.FormatFloat(value, 'f', -1, 64)
	}
	mantissa, exponent, _ := strings.Cut(strconv.FormatFloat(value, 'e', -1, 64), "e")
	return mantissa + "e" + exponent[:1] + strings.TrimLeft(exponent[1:], "0")
}

// jsTrimSpace mirrors upstream `value.trim()`.
func jsTrimSpace(value string) string {
	return strings.TrimFunc(value, isJSSpace)
}

// removeJSSpace mirrors upstream `value.replace(/\s/g, "")`. Invalid UTF-8
// bytes are kept unchanged instead of being replaced like strings.Map does.
func removeJSSpace(value string) string {
	var out strings.Builder
	out.Grow(len(value))
	for index := 0; index < len(value); {
		char, size := utf8.DecodeRuneInString(value[index:])
		if size > 1 || char != utf8.RuneError {
			if !isJSSpace(char) {
				out.WriteString(value[index : index+size])
			}
		} else {
			out.WriteByte(value[index])
		}
		index += size
	}
	return out.String()
}

// decodeUTF8Lossy mirrors WHATWG `new TextDecoder("utf-8")` without fatal,
// which URLSearchParams also uses: each maximal invalid subpart becomes one
// U+FFFD, so a truncated sequence such as E4 B8 yields a single replacement.
func decodeUTF8Lossy(data []byte) string {
	if utf8.Valid(data) {
		return string(data)
	}
	var out strings.Builder
	out.Grow(len(data) + 8)
	for index := 0; index < len(data); {
		char, size := utf8.DecodeRune(data[index:])
		if size > 1 || char != utf8.RuneError {
			out.Write(data[index : index+size])
			index += size
			continue
		}
		index += utf8MaximalSubpart(data[index:])
		out.WriteRune(utf8.RuneError)
	}
	return out.String()
}

// utf8MaximalSubpart returns how many bytes of an invalid sequence at the
// start of data the WHATWG UTF-8 decoder consumes before emitting U+FFFD.
func utf8MaximalSubpart(data []byte) int {
	need, low, high := 0, byte(0x80), byte(0xBF)
	switch lead := data[0]; {
	case lead >= 0xC2 && lead <= 0xDF:
		need = 1
	case lead == 0xE0:
		need, low = 2, 0xA0
	case lead == 0xED:
		need, high = 2, 0x9F
	case lead >= 0xE1 && lead <= 0xEF:
		need = 2
	case lead == 0xF0:
		need, low = 3, 0x90
	case lead == 0xF4:
		need, high = 3, 0x8F
	case lead >= 0xF1 && lead <= 0xF3:
		need = 3
	default:
		return 1
	}
	consumed := 1
	for ; consumed <= need && consumed < len(data); consumed++ {
		if data[consumed] < low || data[consumed] > high {
			break
		}
		low, high = 0x80, 0xBF
	}
	return consumed
}
