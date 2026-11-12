//go:build integration

// 使用真实Go解析器与JSON桥接贯穿批量读取、引用回传、关闭会话和最终提交。
package mobile

import (
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
)

func TestParsedReferenceBridge(t *testing.T) {
	if OpenCatalog(filepath.Join(t.TempDir(), "catalog.db")) != `{"ok":true}` {
		t.Fatal("打开隔离数据库失败")
	}
	t.Cleanup(func() { catalogStore.Close(); catalogStore = nil })
	session := reviewCall(t, "parseBegin", nil)["id"]
	const count = 257
	for i := range count {
		body := fmt.Sprintf("trojan://fixture@node%d.example:443#%s\n", i, strings.Repeat("n", 2048))
		reviewCall(t, "parseChunk", map[string]any{"id": session, "start": i, "payload": body})
	}
	result := reviewCall(t, "parseFinish", map[string]any{"id": session})
	if result["nodeCount"] != float64(count) {
		t.Fatal("解析记录数变化")
	}
	id := reviewCall(t, "add", map[string]any{"address": "local:reference-bridge", "name": "引用"})["id"]
	ticket := reviewCall(t, "begin", map[string]any{"id": id})
	pages := 0
	for start := 0; start < count; {
		page := reviewCall(t, "parsePage", map[string]any{"id": session, "kind": "node", "start": start})
		records := page["records"].([]any)
		if len(records) == 0 {
			t.Fatal("解析结果提前结束")
		}
		encoded, err := json.Marshal(records)
		if err != nil {
			t.Fatal(err)
		}
		if len(encoded) > 900000 {
			t.Fatal("普通解析页超过字节预算")
		}
		refs := make([]map[string]any, len(records))
		for j, raw := range records {
			ordinal := start + j
			node := raw.(map[string]any)
			if node["server"] != fmt.Sprintf("node%d.example", ordinal) {
				t.Fatal("分页顺序错误")
			}
			refs[j] = map[string]any{"session": session, "ordinal": ordinal, "eligible": ordinal%2 == 0, "source": map[string]string{"url": "provider"}}
		}
		reviewCall(t, "stageParsed", map[string]any{"id": id, "generation": ticket["generation"], "start": start, "references": refs})
		start += len(records)
		pages++
	}
	if pages < 2 {
		t.Fatal("夹具未覆盖跨页读取")
	}
	reviewCall(t, "parseEnd", map[string]any{"id": session})
	reviewCall(t, "commit", map[string]any{"refresh": map[string]any{"id": id, "generation": ticket["generation"], "count": count, "usage": nil, "warnings": []string{}}})
	summary := reviewCall(t, "summary", nil)
	if summary["flights"] != float64((count+1)/2) {
		t.Fatal("引用链过滤或提交数量错误")
	}
}
