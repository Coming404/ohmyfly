//go:build integration

// 通过真实移动桥接验证按字节分批后，来源和节点写入不再受100条限制。
package mobile

import (
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
)

func TestByteBudgetCatalogWrites(t *testing.T) {
	if OpenCatalog(filepath.Join(t.TempDir(), "catalog.db")) != `{"ok":true}` {
		t.Fatal("打开隔离数据库失败")
	}
	t.Cleanup(func() { catalogStore.Close(); catalogStore = nil })
	inputs := make([]map[string]any, 257)
	for i := range inputs {
		inputs[i] = map[string]any{"address": fmt.Sprintf("https://source%d.example/sub", i), "name": "来源"}
	}
	added := reviewCall(t, "addSources", map[string]any{"sources": inputs, "merge": true, "name": "合并"})["sources"].([]any)
	if len(added) != len(inputs) {
		t.Fatal("来源批次被条数截断")
	}
	source := added[0].(map[string]any)
	ticket := reviewCall(t, "begin", map[string]any{"id": source["id"]})
	var parsed struct {
		Nodes []map[string]any `json:"nodes"`
	}
	if err := json.Unmarshal([]byte(ParseSubscription("trojan://fixture@node.example:443#node")), &parsed); err != nil || len(parsed.Nodes) != 1 {
		t.Fatal("解析夹具失败")
	}
	parsed.Nodes[0]["eligible"] = true
	nodes := make([]map[string]any, 257)
	for i := range nodes {
		nodes[i] = parsed.Nodes[0]
	}
	reviewCall(t, "stage", map[string]any{"id": source["id"], "generation": ticket["generation"], "start": 0, "nodes": nodes})
	reviewCall(t, "commit", map[string]any{"refresh": map[string]any{"id": source["id"], "generation": ticket["generation"], "count": len(nodes), "usage": nil, "warnings": []string{}}})
	summary := reviewCall(t, "summary", nil)
	if summary["towers"] != float64(1) || summary["flights"] != float64(1) {
		t.Fatal("大批暂存未完整提交或去重错误")
	}
	// 取消固定条数不取消单次请求边界，超限请求在任何写入前拒绝。
	var rejected map[string]any
	if err := json.Unmarshal([]byte(Catalog(strings.Repeat(" ", 1024*1024+1))), &rejected); err != nil {
		t.Fatal(err)
	}
	if rejected["error"] != "目录批次超过 1 MiB" {
		t.Fatal("桥接请求字节上限未生效")
	}
	after := reviewCall(t, "summary", nil)
	if after["revision"] != summary["revision"] {
		t.Fatal("超限请求改变目录")
	}
}
