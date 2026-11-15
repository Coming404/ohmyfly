//go:build integration

// 真实数据只从工作目录读取；通过 App 同款 JSON 桥接执行分块解析与提交。
package mobile

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func reviewCall(t *testing.T, action string, args map[string]any) map[string]any {
	t.Helper()
	if args == nil {
		args = map[string]any{}
	}
	args["action"] = action
	input, err := json.Marshal(args)
	if err != nil {
		t.Fatal(err)
	}
	var result map[string]any
	if err = json.Unmarshal([]byte(Catalog(string(input))), &result); err != nil {
		t.Fatal("桥接响应无效")
	}
	if result["error"] != nil {
		t.Fatalf("桥接操作 %s 失败", action)
	}
	return result
}

func TestRealCatalogBridge(t *testing.T) {
	dir := os.Getenv("REVIEW_DATA_DIR")
	if dir == "" {
		t.Skip("需提供外置订阅目录")
	}
	path := filepath.Join(t.TempDir(), "catalog.db")
	if OpenCatalog(path) != `{"ok":true}` {
		t.Fatal("打开隔离数据库失败")
	}
	t.Cleanup(func() { catalogStore.Close(); catalogStore = nil })
	total := 0
	for _, file := range []string{"source1.yaml", "source2.yaml"} {
		body, err := os.ReadFile(filepath.Join(dir, file))
		if err != nil {
			t.Fatal(err)
		}
		var direct struct {
			Nodes []map[string]any `json:"nodes"`
			Error string           `json:"error"`
		}
		if err = json.Unmarshal([]byte(ParseSubscription(string(body))), &direct); err != nil || direct.Error != "" {
			t.Fatal("直接解析失败")
		}
		parseID := reviewCall(t, "parseBegin", nil)["id"]
		// 按 Unicode 字符切块，验证流式接口与一次性入口完全一致。
		runes := []rune(string(body))
		for start, ordinal := 0, 0; start < len(runes); start, ordinal = start+137, ordinal+1 {
			reviewCall(t, "parseChunk", map[string]any{"id": parseID, "start": ordinal, "payload": string(runes[start:min(start+137, len(runes))])})
		}
		stats := reviewCall(t, "parseFinish", map[string]any{"id": parseID})
		if int(stats["nodeCount"].(float64)) != len(direct.Nodes) {
			t.Fatal("分块解析数量不一致")
		}
		id := reviewCall(t, "add", map[string]any{"address": file, "name": file})["id"]
		ticket := reviewCall(t, "begin", map[string]any{"id": id})
		// 刷新票据只含身份字段，去重配置留在 Go 事务，避免跨端解码空集合。
		if len(ticket) != 4 || ticket["address"] == nil || ticket["name"] == nil {
			t.Fatal("刷新票据桥接契约不完整")
		}
		for i, want := range direct.Nodes {
			node := reviewCall(t, "parseRecord", map[string]any{"id": parseID, "kind": "node", "start": i})
			if !reflect.DeepEqual(node, want) {
				t.Fatalf("第%d个节点分块/直接解析不一致", i)
			}
			node["eligible"] = true
			// 每个真实节点通过大节点分块入口写两遍，正式库必须去重。
			payload, err := json.Marshal(node)
			if err != nil {
				t.Fatal(err)
			}
			// UTF-8 字节切点移到完整字符边界。
			split := len(payload) / 2
			for split > 0 && payload[split]&0xc0 == 0x80 {
				split--
			}
			pieces := []string{string(payload[:split]), string(payload[split:])}
			for duplicate := 0; duplicate < 2; duplicate++ {
				offset := 0
				for part, piece := range pieces {
					reviewCall(t, "chunk", map[string]any{"id": id, "generation": ticket["generation"], "start": 2*i + duplicate, "offset": offset, "payload": piece, "final": part == 1})
					offset += len(piece)
				}
			}
		}
		reviewCall(t, "commit", map[string]any{"refresh": map[string]any{"id": id, "generation": ticket["generation"], "count": len(direct.Nodes) * 2, "usage": nil, "warnings": []string{}}})
		reviewCall(t, "parseEnd", map[string]any{"id": parseID})
		total += len(direct.Nodes)
	}
	summary := reviewCall(t, "summary", nil)
	if int(summary["flights"].(float64)) != total {
		t.Fatalf("重复节点未消除或原节点丢失: 输入%d 汇总%v", total, summary["flights"])
	}
	t.Logf("真实节点=%d，分块重复写入=%d，正式汇总=%v", total, total*2, summary["flights"])
}
