// SSH 节点的加载前检查：按 mihomo v1.19.33 的 SSH 适配器拒绝它无法加载的节点。
// 接入 mihomo 后改用 adapter.ParseProxy 检查，届时删除本文件。
package flight

import (
	"fmt"
	"strings"
)

// validateSSH 要求 username 非空，并且 password 或 private-key 至少有一个。
// mihomo 的 SshOption.UserName 没有 omitempty（adapter/outbound/ssh.go:36），缺少时加载报错。
// 两种认证都没有时 NewSsh 不报错，但握手没有认证方式；上游 proxy-sanitizer.ts 同样要求至少一个。
func validateSSH(node map[string]any) error {
	if !nonBlankString(node["username"]) {
		return fmt.Errorf("missing ssh username")
	}
	if !nonBlankString(node["password"]) && !nonBlankString(node["private-key"]) {
		return fmt.Errorf("missing ssh password or private-key")
	}
	return nil
}

func nonBlankString(value any) bool {
	text, ok := value.(string)
	return ok && strings.TrimSpace(text) != ""
}
