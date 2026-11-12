//go:build integration

// 通过真实移动桥接并行解析独立会话，检查目录查询不会污染节点结果。
package mobile

import (
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

func TestConcurrentCatalogParsing(t *testing.T) {
	if OpenCatalog(filepath.Join(t.TempDir(), "catalog.db")) != `{"ok":true}` {
		t.Fatal("打开隔离数据库失败")
	}
	t.Cleanup(func() { catalogStore.Close(); catalogStore = nil })
	var wg sync.WaitGroup
	start := make(chan struct{})
	for i := range 8 {
		wg.Go(func() {
			<-start
			id := reviewCall(t, "parseBegin", nil)["id"]
			body := strings.Repeat(fmt.Sprintf("trojan://fixture@node%d.example:443#node%d\n", i, i), 120)
			reviewCall(t, "parseChunk", map[string]any{"id": id, "start": 0, "payload": body})
			result := reviewCall(t, "parseFinish", map[string]any{"id": id})
			if result["nodeCount"].(float64) != 120 {
				t.Error("并发解析节点数量错误")
			}
			node := reviewCall(t, "parseRecord", map[string]any{"id": id, "kind": "node", "start": 119})
			if node["server"] != fmt.Sprintf("node%d.example", i) {
				t.Error("并发解析串入其他会话内容")
			}
			reviewCall(t, "parseEnd", map[string]any{"id": id})
		})
	}
	wg.Go(func() {
		<-start
		for range 30 {
			reviewCall(t, "summary", nil)
		}
	})
	close(start)
	wg.Wait()
}
