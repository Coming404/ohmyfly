//go:build integration

// 补齐真实订阅未覆盖的协议，并验证凭据变化与多名称刷新边界。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"go.yaml.in/yaml/v3"
)

func TestSupplementalProtocols(t *testing.T) {
	cases := []struct {
		name, typ string
		fields    map[string]any
	}{
		{"ssr", "ssr", map[string]any{"cipher": "aes-256-cfb", "password": "review-secret", "protocol": "auth_sha1_v4", "obfs": "tls1.2_ticket_auth"}},
		{"hysteria", "hysteria", map[string]any{"auth-str": "review-secret", "up": "10 Mbps", "down": "20 Mbps"}},
		{"tuic", "tuic", map[string]any{"uuid": "11111111-1111-4111-8111-111111111111", "password": "review-secret", "congestion-controller": "bbr"}},
		{"anytls", "anytls", map[string]any{"password": "review-secret"}},
		{"wireguard", "wireguard", map[string]any{"private-key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=", "public-key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI=", "ip": "10.0.0.2"}},
		{"snell", "snell", map[string]any{"psk": "review-secret", "version": 4}},
		{"socks4", "socks4", map[string]any{"username": "review", "password": "review-secret"}},
		{"socks5", "socks5", map[string]any{"username": "review", "password": "review-secret"}},
		{"http", "http", map[string]any{"username": "review", "password": "review-secret"}},
		{"https", "https", map[string]any{"username": "review", "password": "review-secret", "tls": true}},
		{"ssh", "ssh", map[string]any{"username": "review", "password": "review-secret"}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			raw := tc.fields
			raw["name"] = tc.name
			raw["type"] = tc.typ
			raw["server"] = "review.example"
			raw["port"] = 443
			text, err := yaml.Marshal(map[string]any{"proxies": []map[string]any{raw}})
			if err != nil {
				t.Fatal(err)
			}
			nodes := reviewParse(t, string(text))
			if len(nodes) != 1 || nodes[0].Type != tc.typ {
				t.Fatal("协议解析不符")
			}
			s := reviewStore(t)
			reviewImport(t, s, "one", append(nodes, nodes...))
			reviewImport(t, s, "two", nodes)
			if reviewCount(t, s, "nodes") != 1 || reviewCount(t, s, "tower_nodes") != 3 {
				t.Fatal("重复节点或来源处理错误")
			}
		})
	}
	for _, kind := range []string{"socks", "http", "https"} {
		t.Run("tg-"+kind, func(t *testing.T) {
			nodes := reviewParse(t, "tg://"+kind+"?server=review.example&port=443&user=review&pass=secret&remark=TG")
			want := kind
			if kind == "socks" {
				want = "socks5"
			}
			if len(nodes) != 1 || nodes[0].Type != want {
				t.Fatal("TG协议转换错误")
			}
			s := reviewStore(t)
			reviewImport(t, s, "tg", append(nodes, nodes...))
			if reviewCount(t, s, "nodes") != 1 {
				t.Fatal("TG重复节点未合并")
			}
		})
	}
}

func reviewNode(t *testing.T, name string) Node {
	t.Helper()
	return reviewParse(t, `proxies: [{name: "`+name+`", type: trojan, server: review.example, port: 443, password: secret}]`)[0]
}

func TestRefreshBoundaries(t *testing.T) {
	t.Run("different-credentials", func(t *testing.T) {
		s := reviewStore(t)
		a := reviewNode(t, "A")
		b := a
		var fields map[string]any
		json.Unmarshal(b.Fields, &fields)
		fields["password"] = "different"
		b.Fields, _ = json.Marshal(fields)
		reviewImport(t, s, "one", []Node{a, b})
		if reviewCount(t, s, "nodes") != 2 {
			t.Fatal("不同密码被误合并")
		}
	})
	t.Run("multi-name", func(t *testing.T) {
		s := reviewStore(t)
		a := reviewNode(t, "A")
		b := a
		b.Name = "B"
		reviewImport(t, s, "one", []Node{a, b, a, b})
		if reviewCount(t, s, "nodes") != 2 {
			t.Fatal("多名称例外丢失")
		}
		reviewImport(t, s, "two", []Node{a, b})
		if reviewCount(t, s, "nodes") != 2 {
			t.Fatal("多名称跨塔台重复")
		}
	})
	t.Run("empty-refresh-clears-name-classification", func(t *testing.T) {
		s := reviewStore(t)
		a := reviewNode(t, "A")
		b := a
		b.Name = "B"
		reviewImport(t, s, "one", []Node{a, b})
		reviewImport(t, s, "one", nil)
		reviewImport(t, s, "one", []Node{a})
		reviewImport(t, s, "two", []Node{b})
		if reviewCount(t, s, "nodes") != 1 {
			t.Fatal("历史多名称分类导致空快照之后仍重复入库")
		}
	})
	t.Run("stale-and-incomplete-retain-old", func(t *testing.T) {
		ctx := context.Background()
		s := reviewStore(t)
		id := reviewImport(t, s, "one", []Node{reviewNode(t, "A")})
		old, err := s.Begin(ctx, id)
		if err != nil {
			t.Fatal(err)
		}
		fresh, err := s.Begin(ctx, id)
		if err != nil {
			t.Fatal(err)
		}
		if err = s.Commit(ctx, Refresh{ID: id, Generation: old.Generation}); !errors.Is(err, ErrStale) {
			t.Fatal("未拒绝迟到提交")
		}
		if err = s.Commit(ctx, Refresh{ID: id, Generation: fresh.Generation, Count: 1}); err == nil {
			t.Fatal("未拒绝不完整提交")
		}
		if reviewCount(t, s, "nodes") != 1 || reviewCount(t, s, "tower_nodes") != 1 {
			t.Fatal("失败未保留旧数据")
		}
	})
}

// 多名称不再同时存在时，后续刷新恢复普通身份并保留其他塔台来源。
func TestMultiNameReturnsToOrdinary(t *testing.T) {
	s := reviewStore(t)
	a := reviewNode(t, "A")
	b := a
	b.Name = "B"
	id := reviewImport(t, s, "one", []Node{a, b})
	reviewImport(t, s, "two", []Node{b})
	reviewImport(t, s, "one", []Node{a})
	// 下一次刷新只看到各塔台单名称，不应永久分裂为两个正式节点。
	reviewImport(t, s, "one", []Node{a})
	if reviewCount(t, s, "nodes") != 1 || reviewCount(t, s, "tower_nodes") != 2 {
		t.Fatal("例外退出后未合并节点或丢失来源")
	}
	if err := s.Remove(context.Background(), id); err != nil {
		t.Fatal(err)
	}
	if reviewCount(t, s, "nodes") != 1 {
		t.Fatal("删除一个来源误删共享节点")
	}
}

func TestContentHashContract(t *testing.T) {
	base := map[string]any{"type": "trojan", "server": "review.example", "port": 443, "password": "secret"}
	hash := flight.ContentHash(flight.Flight{Fields: base})
	for _, field := range []string{"name", "dialer-proxy", "udp", "tfo", "client-fingerprint"} {
		t.Run("ignore-"+field, func(t *testing.T) {
			copy := map[string]any{}
			for k, v := range base {
				copy[k] = v
			}
			copy[field] = true
			if field != "udp" && field != "tfo" {
				copy[field] = "changed"
			}
			if flight.ContentHash(flight.Flight{Fields: copy}) != hash {
				t.Fatal("忽略字段改变内容哈希")
			}
		})
	}
	for _, field := range []string{"password", "fingerprint", "server", "port", "network", "uuid"} {
		t.Run("compare-"+field, func(t *testing.T) {
			copy := map[string]any{}
			for k, v := range base {
				copy[k] = v
			}
			copy[field] = "changed"
			if field == "port" {
				copy[field] = 8443
			}
			if flight.ContentHash(flight.Flight{Fields: copy}) == hash {
				t.Fatal("连接字段未参与内容哈希")
			}
		})
	}
}
