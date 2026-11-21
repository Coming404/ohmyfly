package flight

// sublinkPro Proxy 标量类型，避免 Fields 动态类型改变同一协议字段的身份。
var proxyIntegerFields = map[string]bool{
	"port":                        true,
	"hop-interval":                true,
	"up":                          true,
	"down":                        true,
	"idle-session-check-interval": true,
	"idle-session-timeout":        true,
	"min-idle-session":            true,
	"up-speed":                    true,
	"down-speed":                  true,
	"routing-mark":                true,
	"mtu":                         true,
	"version":                     true,
	"ping":                        true,
	"ping-restart":                true,
	"tran-window":                 true,
	"handshake-timeout":           true,
}

var proxyBooleanFields = map[string]bool{
	"tfo":                        true,
	"udp":                        true,
	"skip-cert-verify":           true,
	"tls":                        true,
	"disable-sni":                true,
	"mptcp":                      true,
	"remote-dns-resolve":         true,
	"ephemeral":                  true,
	"accept-routes":              true,
	"exit-node-allow-lan-access": true,
}
