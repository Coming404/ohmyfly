//go:build integration

// 使用外置真实订阅验证解析与持久化；日志只包含协议、序号和数量。
package catalog

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"github.com/Coming404/ohmyfly/native-core/internal/towerparse"
	"go.yaml.in/yaml/v3"
)

func reviewStore(t *testing.T) *Store {
	t.Helper()
	s, err := Open(context.Background(), filepath.Join(t.TempDir(), "catalog.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { s.Close() })
	return s
}

func reviewParse(t *testing.T, text string) []Node {
	t.Helper()
	var nodes []Node
	stats, err := towerparse.Parse(strings.NewReader(text), func(f flight.Flight) error {
		fields, err := json.Marshal(f.Fields)
		if err != nil {
			return err
		}
		nodes = append(nodes, Node{Name: f.Name, Type: f.Type, Server: f.Server, Port: f.Port, Fields: fields,
			RawInput: f.RawInput, SourceFormat: f.SourceFormat, ContentHash: flight.ContentHash(f), Eligible: true, Source: json.RawMessage(`{}`)})
		return nil
	})
	if err != nil {
		t.Fatal("解析失败")
	}
	if stats.Skipped != 0 {
		t.Fatalf("解析跳过 %d 个节点", stats.Skipped)
	}
	return nodes
}

func reviewImport(t *testing.T, s *Store, address string, nodes []Node) int64 {
	t.Helper()
	ctx := context.Background()
	id, err := s.Add(ctx, address, address)
	if err != nil {
		t.Fatal(err)
	}
	ticket, err := s.Begin(ctx, id)
	if err != nil {
		t.Fatal(err)
	}
	for start := 0; start < len(nodes); start += 100 {
		end := min(start+100, len(nodes))
		if err = s.Stage(ctx, id, ticket.Generation, start, nodes[start:end]); err != nil {
			t.Fatal(err)
		}
	}
	err = s.Commit(ctx, Refresh{ID: id, Generation: ticket.Generation, Count: len(nodes), Usage: json.RawMessage(`null`), Warnings: json.RawMessage(`[]`)})
	if err != nil {
		t.Fatal(err)
	}
	return id
}

func reviewCount(t *testing.T, s *Store, table string) int {
	t.Helper()
	var count int
	if err := s.db.QueryRow("SELECT count(*) FROM " + table).Scan(&count); err != nil {
		t.Fatal(err)
	}
	return count
}

// 对照输入而非另一次解析结果，确保真实凭据没有在规范化中丢失。
func reviewCredentials(t *testing.T, ordinal int, raw map[string]any, node Node) {
	t.Helper()
	var fields map[string]any
	if err := json.Unmarshal(node.Fields, &fields); err != nil {
		t.Fatal(err)
	}
	for _, key := range []string{"password", "uuid", "username", "cipher", "fingerprint", "private-key", "public-key", "psk"} {
		if original, ok := raw[key]; ok && fmt.Sprint(original) != fmt.Sprint(fields[key]) {
			t.Fatalf("第%d个节点的%s字段与原文不符", ordinal, key)
		}
	}
	if fmt.Sprint(raw["port"]) != fmt.Sprint(node.Port) {
		t.Fatalf("第%d个节点端口不符", ordinal)
	}
}

func TestRealSubscriptions(t *testing.T) {
	dir := os.Getenv("REVIEW_DATA_DIR")
	if dir == "" {
		t.Skip("需提供外置真实订阅目录 REVIEW_DATA_DIR")
	}
	for _, file := range []string{"source1.yaml", "source2.yaml"} {
		t.Run(file, func(t *testing.T) {
			data, err := os.ReadFile(filepath.Join(dir, file))
			if err != nil {
				t.Fatal(err)
			}
			var doc struct {
				Proxies []map[string]any `yaml:"proxies"`
			}
			if err = yaml.Unmarshal(data, &doc); err != nil {
				t.Fatal("源 YAML 无法读取")
			}
			all := reviewParse(t, string(data))
			if len(all) != len(doc.Proxies) {
				t.Fatalf("输入=%d 解析=%d", len(doc.Proxies), len(all))
			}
			counts := map[string]int{}
			s := reviewStore(t)
			for i, raw := range doc.Proxies {
				text, err := yaml.Marshal(map[string]any{"proxies": []map[string]any{raw}})
				if err != nil {
					t.Fatal(err)
				}
				single := reviewParse(t, string(text))
				if len(single) != 1 {
					t.Fatalf("第%d个节点未输出", i)
				}
				n := single[0]
				if n.ContentHash == "" || n.Server == "" || n.Port <= 0 {
					t.Fatalf("第%d个节点未规范化", i)
				}
				if n.ContentHash != all[i].ContentHash {
					t.Fatalf("第%d个节点单独/批量解析不一致", i)
				}
				reviewCredentials(t, i, raw, n)
				counts[n.Type]++
			}
			first := reviewImport(t, s, "source-A", all)
			for i, expected := range all {
				var stored string
				if err := s.db.QueryRow("SELECT n.config FROM nodes n JOIN tower_nodes t ON t.node_id=n.id WHERE t.tower_id=? AND t.ordinal=?", first, i).Scan(&stored); err != nil {
					t.Fatal(err)
				}
				var actual Node
				if err := json.Unmarshal([]byte(stored), &actual); err != nil {
					t.Fatal(err)
				}
				if !reflect.DeepEqual(actual, expected) {
					t.Fatalf("第%d个节点入库后配置不一致", i)
				}
			}
			unique := reviewCount(t, s, "nodes")
			doubled := append(append([]Node{}, all...), all...)
			reviewImport(t, s, "source-A", doubled)
			if got := reviewCount(t, s, "nodes"); got != unique {
				t.Fatalf("批内重复未消除: %d -> %d", unique, got)
			}
			reviewImport(t, s, "source-B", all)
			if got := reviewCount(t, s, "nodes"); got != unique {
				t.Fatalf("跨塔台重复未消除: %d -> %d", unique, got)
			}
			if got := reviewCount(t, s, "tower_nodes"); got != len(all)*3 {
				t.Fatalf("来源丢失: %d", got)
			}
			if err = s.Remove(context.Background(), first); err != nil {
				t.Fatal(err)
			}
			if got := reviewCount(t, s, "nodes"); got != unique {
				t.Fatal("删除塔台误删共享节点")
			}
			t.Logf("输入=%d 逐节点通过=%d 正式节点=%d 手动重复=%d 协议=%v", len(all), len(all), unique, len(all), counts)
		})
	}
}
