package towerparse

import (
	"fmt"
	"net"
	"net/url"
	"strings"
)

// linkDefaultName names a node whose link carries no name, so unnamed
// nodes stay distinguishable (IPv6 servers get brackets).
func linkDefaultName(server string, port any) string {
	return net.JoinHostPort(server, jsTrimSpace(fmt.Sprint(port)))
}

// addLinkPinnedCertificate 处理 Xray 的 pcs 与 hpkp：fingerprint 写第一个有效 SHA-256，
// 供 mihomo 校验证书（它只接受一个值）；pcs 原样保留，供导出分享链接时写回多个指纹和原始写法。
func addLinkPinnedCertificate(node map[string]any, query url.Values) {
	if fingerprint := linkCertificateFingerprint(linkString(query, "pcs"), linkString(query, "hpkp")); fingerprint != "" {
		node["fingerprint"] = fingerprint
	}
	if pcs := query.Get("pcs"); pcs != "" {
		node["pcs"] = pcs
	}
}

// linkCertificateFingerprint returns the first value that is a SHA-256
// certificate fingerprint (64 hex digits, ":" and "-" ignored), lowercased.
// Values may be comma separated. Anything else yields "".
func linkCertificateFingerprint(values ...string) string {
	for _, value := range values {
		for _, part := range strings.Split(value, ",") {
			cleaned := strings.NewReplacer(":", "", "-", "").Replace(jsTrimSpace(part))
			if len(cleaned) == 64 && isHexString(cleaned) {
				return strings.ToLower(cleaned)
			}
		}
	}
	return ""
}

func isHexString(value string) bool {
	for _, c := range value {
		if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F') {
			return false
		}
	}
	return true
}
