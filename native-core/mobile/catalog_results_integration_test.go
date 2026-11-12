//go:build integration

package mobile

import (
	"encoding/json"
	"path/filepath"
	"testing"
	"time"
)

func resultBridgeNodes(t *testing.T) ([]any, float64) {
	t.Helper()
	if OpenCatalog(filepath.Join(t.TempDir(), "results.db")) != `{"ok":true}` {
		t.Fatal("open result bridge database")
	}
	t.Cleanup(func() { catalogStore.Close(); catalogStore = nil })
	var parsed struct {
		Nodes []map[string]any `json:"nodes"`
	}
	if err := json.Unmarshal([]byte(ParseSubscription("proxies:\n  - {name: A, type: ss, server: a.test, port: 443, cipher: aes-128-gcm, password: test}\n  - {name: B, type: ss, server: b.test, port: 443, cipher: aes-128-gcm, password: test}\n")), &parsed); err != nil || len(parsed.Nodes) != 2 {
		t.Fatal("parse result bridge fixtures")
	}
	for _, node := range parsed.Nodes {
		node["eligible"] = true
	}
	tower := reviewCall(t, "add", map[string]any{"address": "https://example.test/sub"})["id"]
	ticket := reviewCall(t, "begin", map[string]any{"id": tower})
	reviewCall(t, "stage", map[string]any{"id": tower, "generation": ticket["generation"], "nodes": parsed.Nodes})
	reviewCall(t, "commit", map[string]any{"refresh": map[string]any{"id": tower, "generation": ticket["generation"], "count": 2}})
	revision := reviewCall(t, "summary", nil)["revision"].(float64)
	filter := reviewCall(t, "beginFilter", map[string]any{"revision": revision})
	inputs := reviewCall(t, "filterInputs", map[string]any{"filterTicket": filter})["inputs"].([]any)
	return []any{inputs[0].(map[string]any)["id"], inputs[1].(map[string]any)["id"]}, revision
}

func TestCatalogResultsJSONContract(t *testing.T) {
	ids, revision := resultBridgeNodes(t)
	settings := map[string]any{"mode": "Latency", "latencySamples": 6}
	task := reviewCall(t, "taskCreate", map[string]any{"mode": "Latency", "settings": settings, "nodeIds": ids[:1]})
	if task["id"].(float64) <= 0 || task["status"] != "queued" || task["startedAt"] != nil || task["settings"].(map[string]any)["mode"] != "Latency" {
		t.Fatal("task snapshot, identity or nullable time contract")
	}
	reviewCall(t, "taskAddNodes", map[string]any{"id": task["id"], "nodeIds": ids[1:]})
	reviewCall(t, "taskUpdate", map[string]any{"id": task["id"], "status": "running"})
	history := reviewCall(t, "results", map[string]any{"taskId": task["id"], "limit": 100})
	run := history["runs"].([]any)[0].(map[string]any)
	reviewCall(t, "runUpdate", map[string]any{"id": run["id"], "status": "running"})
	now := time.Now().UnixMilli()
	reviewCall(t, "metricWrite", map[string]any{"metric": map[string]any{"runId": run["id"], "kind": "latency", "status": "succeeded", "value": 0, "measuredAt": now}})
	reviewCall(t, "platformWrite", map[string]any{"platform": map[string]any{"runId": run["id"], "platform": "custom_stream", "status": "unlocked", "region": "JP", "checkedAt": now}})
	reviewCall(t, "taskUpdate", map[string]any{"id": task["id"], "status": "cancelled"})
	if reviewCall(t, "task", map[string]any{"id": task["id"]})["status"] != "cancelled" {
		t.Fatal("task read contract")
	}
	batch := reviewCall(t, "measurements", map[string]any{"nodeIds": ids, "revision": revision})
	if batch["revision"] != revision || batch["metricsRevision"].(float64) <= history["metricsRevision"].(float64) {
		t.Fatal("result version must advance without invalidating directory")
	}
	for _, value := range batch["nodes"].([]any) {
		node := value.(map[string]any)
		platforms := node["platforms"].([]any)
		if node["nodeId"] != run["nodeId"] {
			if len(platforms) != 0 {
				t.Fatal("untested node platforms must be []")
			}
			continue
		}
		platform := platforms[0].(map[string]any)
		if len(platform) != 4 || platform["platform"] != "custom_stream" || platform["status"] != "unlocked" || platform["region"] != "JP" || platform["checkedAt"] != float64(now) {
			t.Fatal("platform summary JSON differs from Kotlin DTO")
		}
		metric := node["metrics"].(map[string]any)["latency"].(map[string]any)["valid"].(map[string]any)
		if metric["value"] != float64(0) || metric["measuredAt"] != float64(now) {
			t.Fatal("zero or millisecond measurement lost")
		}
	}
	raw := Catalog(`{"action":"measurements","nodeIds":[],"revision":0}`)
	var stale map[string]any
	if json.Unmarshal([]byte(raw), &stale) != nil || stale["stale"] != true || stale["error"] == nil {
		t.Fatal("stale revision JSON contract")
	}
}
