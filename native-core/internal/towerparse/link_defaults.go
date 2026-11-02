package towerparse

import (
	"fmt"
	"net"
	"strings"
)

// linkDefaultName names a node whose link carries no name, so unnamed
// nodes stay distinguishable (IPv6 servers get brackets).
func linkDefaultName(server string, port any) string {
	return net.JoinHostPort(server, strings.TrimSpace(fmt.Sprint(port)))
}

// linkCertificateFingerprint returns the first value that is a SHA-256
// certificate fingerprint (64 hex digits, ":" and "-" ignored), lowercased.
// Values may be comma separated. Anything else yields "".
func linkCertificateFingerprint(values ...string) string {
	for _, value := range values {
		for _, part := range strings.Split(value, ",") {
			cleaned := strings.NewReplacer(":", "", "-", "").Replace(strings.TrimSpace(part))
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
