//go:build integration

package catalog

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func requireResult(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}
func resultNode(t *testing.T, s *Store) int64 {
	t.Helper()
	var id int64
	requireResult(t, s.db.QueryRow("SELECT min(id) FROM nodes").Scan(&id))
	return id
}
func startResult(t *testing.T, s *Store, node int64, mode string) (Task, NodeRun) {
	t.Helper()
	ctx := context.Background()
	snapshot := json.RawMessage(fmt.Sprintf(`{"mode":%q,"latencySamples":6,"downloadMiB":10}`, mode))
	task, err := s.CreateTask(ctx, mode, snapshot, []int64{node})
	requireResult(t, err)
	requireResult(t, s.UpdateTask(ctx, task.ID, "running", ""))
	page, err := s.Results(ctx, 0, task.ID, 0, 100)
	requireResult(t, err)
	run := page.Runs[0]
	requireResult(t, s.UpdateRun(ctx, run.ID, "running", ""))
	return task, run
}
func saveMetric(t *testing.T, s *Store, run int64, kind, status string, value float64) {
	t.Helper()
	now := time.Now().UnixMilli()
	metric := Metric{RunID: run, Kind: kind, Status: status, MeasuredAt: &now}
	if status == "succeeded" {
		metric.Value = &value
	} else {
		metric.Reason = "test failure"
	}
	requireResult(t, s.WriteMetric(context.Background(), metric))
}

func TestResultLifecycleAndPartialMeasurements(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	reviewImport(t, s, "one", []Node{reviewNode(t, "A")})
	node := resultNode(t, s)
	ticket := reviewFilter(t, s, func(FilterInput) bool { return true })
	task, run := startResult(t, s, node, "Full")
	for _, kind := range []string{"latency", "download", "upload"} {
		saveMetric(t, s, run.ID, kind, "succeeded", 12.5)
	}
	requireResult(t, s.UpdateRun(ctx, run.ID, "succeeded", ""))
	requireResult(t, s.UpdateTask(ctx, task.ID, "succeeded", ""))
	history, err := s.Task(ctx, task.ID)
	requireResult(t, err)
	if history.Mode != "Full" || history.StartedAt == nil || history.FinishedAt == nil || string(history.Settings) != string(task.Settings) {
		t.Fatal("task snapshot or lifecycle lost")
	}
	initial, err := s.Measurements(ctx, []int64{node}, ticket.Revision)
	requireResult(t, err)
	speed := initial.Nodes[0].Metrics["download"].Valid
	second, latency := startResult(t, s, node, "Latency")
	if err = s.WriteMetric(ctx, Metric{RunID: latency.ID, Kind: "download", Status: "running"}); err == nil {
		t.Fatal("mode accepted download")
	}
	saveMetric(t, s, latency.ID, "latency", "failed", 0)
	requireResult(t, s.UpdateRun(ctx, latency.ID, "failed", "timeout"))
	requireResult(t, s.UpdateTask(ctx, second.ID, "failed", "timeout"))
	latest, err := s.Measurements(ctx, []int64{node}, ticket.Revision)
	requireResult(t, err)
	metrics := latest.Nodes[0].Metrics
	if *metrics["download"].Valid.MeasuredAt != *speed.MeasuredAt || metrics["download"].Valid.RunID != run.ID || metrics["upload"].Valid == nil {
		t.Fatal("partial update replaced previous speeds")
	}
	if metrics["latency"].Latest.Status != "failed" || metrics["latency"].Latest.Value != nil || metrics["latency"].Valid.RunID != run.ID {
		t.Fatal("failed result erased valid value or reused timestamp")
	}
	page, err := s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	requireResult(t, err)
	if page.MatchCount != 1 || page.MetricsRevision <= initial.MetricsRevision || page.Revision != ticket.Revision {
		t.Fatal("metrics invalidated directory or did not advance")
	}
	if err = s.WriteMetric(ctx, Metric{RunID: run.ID, Kind: "latency", Status: "running"}); err == nil {
		t.Fatal("terminal run reopened")
	}
}

func TestResultCancellationAndRestart(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "restart.db")
	s, err := Open(ctx, path)
	requireResult(t, err)
	reviewImport(t, s, "one", []Node{reviewNode(t, "A")})
	node := resultNode(t, s)
	task, run := startResult(t, s, node, "Full")
	saveMetric(t, s, run.ID, "latency", "succeeded", 0)
	requireResult(t, s.WriteMetric(ctx, Metric{RunID: run.ID, Kind: "download", Status: "running"}))
	requireResult(t, s.WritePlatform(ctx, PlatformResult{RunID: run.ID, Platform: "netflix", Status: "running"}))
	requireResult(t, s.UpdateTask(ctx, task.ID, "cancelled", "user cancelled"))
	page, err := s.Results(ctx, node, 0, 0, 100)
	requireResult(t, err)
	if page.Runs[0].Status != "cancelled" || page.Runs[0].Platforms[0].Status != "cancelled" {
		t.Fatal("cancel did not close phases")
	}
	task, run = startResult(t, s, node, "Download")
	requireResult(t, s.WriteMetric(ctx, Metric{RunID: run.ID, Kind: "download", Status: "running"}))
	requireResult(t, s.Close())
	s, err = Open(ctx, path)
	requireResult(t, err)
	t.Cleanup(func() { s.Close() })
	restored, err := s.Task(ctx, task.ID)
	requireResult(t, err)
	page, err = s.Results(ctx, node, 0, 0, 1)
	requireResult(t, err)
	if restored.Status != "interrupted" || restored.FinishedAt == nil || page.Runs[0].Metrics[0].Status != "interrupted" {
		t.Fatal("restart left running state")
	}
	older, err := s.Results(ctx, node, 0, page.Runs[0].ID, 1)
	requireResult(t, err)
	if len(older.Runs) != 1 || older.Runs[0].ID == page.Runs[0].ID {
		t.Fatal("history cursor repeated a run")
	}
	for _, metric := range older.Runs[0].Metrics {
		if metric.Kind == "latency" && (metric.Status != "succeeded" || *metric.Value != 0) {
			t.Fatal("zero is not a successful measurement")
		}
	}
}

func TestPlatformStatesAndSourceLifetime(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	a := reviewNode(t, "A")
	first := reviewImport(t, s, "one", []Node{a})
	second := reviewImport(t, s, "two", []Node{a})
	node := resultNode(t, s)
	task, run := startResult(t, s, node, "Latency")
	now := time.Now().UnixMilli()
	for _, p := range []PlatformResult{
		{RunID: run.ID, Platform: "netflix", Status: "unlocked", Region: "US", CheckedAt: &now},
		{RunID: run.ID, Platform: "disney", Status: "locked", CheckedAt: &now},
		{RunID: run.ID, Platform: "chatgpt", Status: "failed", CheckedAt: &now, Reason: "timeout"},
	} {
		requireResult(t, s.WritePlatform(ctx, p))
	}
	summary, err := s.Summary(ctx)
	requireResult(t, err)
	measured, err := s.Measurements(ctx, []int64{node}, summary.Revision)
	requireResult(t, err)
	platforms := measured.Nodes[0].Platforms
	if len(platforms) != 3 || platforms[0].Platform != "chatgpt" || platforms[0].Status != "failed" ||
		platforms[1].Platform != "disney" || platforms[1].Status != "locked" ||
		platforms[2].Platform != "netflix" || platforms[2].Status != "unlocked" ||
		platforms[2].Region != "US" || platforms[2].CheckedAt == nil || *platforms[2].CheckedAt != now {
		t.Fatal("platform identity, status, region or time lost")
	}
	requireResult(t, s.UpdateTask(ctx, task.ID, "failed", "platform failed"))
	reviewImport(t, s, "one", []Node{a})
	if reviewCount(t, s, "platform_results") != 3 {
		t.Fatal("refresh erased platform results")
	}
	requireResult(t, s.Remove(ctx, first))
	if reviewCount(t, s, "node_runs") != 1 {
		t.Fatal("shared result removed early")
	}
	requireResult(t, s.Remove(ctx, second))
	if reviewCount(t, s, "node_runs") != 0 || reviewCount(t, s, "platform_results") != 0 {
		t.Fatal("orphan results retained")
	}
}

func TestResultMergeAndDifferentConfiguration(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	a := reviewNode(t, "A")
	b := a
	b.Name = "B"
	reviewImport(t, s, "one", []Node{a, b})
	rows, err := s.db.Query("SELECT id FROM nodes ORDER BY id")
	requireResult(t, err)
	ids := []int64{}
	for rows.Next() {
		var id int64
		requireResult(t, rows.Scan(&id))
		ids = append(ids, id)
	}
	requireResult(t, rows.Close())
	if len(ids) != 2 {
		t.Fatal("multi-name fixture did not retain two nodes")
	}
	for _, id := range ids {
		task, run := startResult(t, s, id, "Latency")
		saveMetric(t, s, run.ID, "latency", "succeeded", float64(id))
		requireResult(t, s.UpdateTask(ctx, task.ID, "cancelled", "done"))
	}
	reviewImport(t, s, "one", []Node{a})
	page, err := s.Results(ctx, resultNode(t, s), 0, 0, 100)
	requireResult(t, err)
	if len(page.Runs) != 2 || page.Runs[0].NodeID != page.Runs[1].NodeID {
		t.Fatal("same-config history not migrated")
	}
	other := reviewParse(t, "proxies:\n  - {name: C, type: ss, server: different.example, port: 443, cipher: aes-128-gcm, password: fixture}\n")[0]
	reviewImport(t, s, "one", []Node{other})
	if reviewCount(t, s, "node_runs") != 0 {
		t.Fatal("different configuration inherited results")
	}
}

func TestNonePaginationCountPlanAndInvalidation(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	nodes := make([]Node, 205)
	for i := range nodes {
		nodes[i] = reviewParse(t, fmt.Sprintf("proxies:\n  - {name: N%d, type: ss, server: n%d.example, port: 443, cipher: aes-128-gcm, password: fixture}\n", i, i))[0]
	}
	tower := reviewImport(t, s, "one", nodes)
	reviewImport(t, s, "two", nodes)
	ticket := reviewFilter(t, s, func(FilterInput) bool { return true })
	var anchor int64
	seen := map[int64]bool{}
	for range 3 {
		page, err := s.Page(ctx, "flights", anchor, false, ticket.Revision, ticket.Token)
		requireResult(t, err)
		if page.MatchCount != 205 {
			t.Fatal("count drift")
		}
		for _, flight := range page.Flights {
			if seen[flight.ID] {
				t.Fatal("repeated node")
			}
			seen[flight.ID] = true
			anchor = flight.ID
		}
	}
	if len(seen) != 205 {
		t.Fatal("missing nodes")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	requireResult(t, err)
	query, err := candidateQuery(ctx, tx, true)
	requireResult(t, err)
	rows, err := tx.QueryContext(ctx, "EXPLAIN QUERY PLAN "+query+"SELECT n.id FROM matched c JOIN nodes n ON n.id=c.id WHERE n.id>? ORDER BY n.id LIMIT 100", 0)
	requireResult(t, err)
	plans := []string{}
	for rows.Next() {
		var id, parent, unused int
		var detail string
		requireResult(t, rows.Scan(&id, &parent, &unused, &detail))
		plans = append(plans, detail)
	}
	requireResult(t, rows.Close())
	requireResult(t, tx.Rollback())
	plan := strings.Join(plans, "\n")
	t.Log(plan)
	if strings.Contains(plan, "TEMP B-TREE") || strings.Contains(plan, "CO-ROUTINE") || !strings.Contains(plan, "COVERING INDEX tower_nodes_") {
		t.Fatal("none query did not use direct indexed path")
	}
	requireResult(t, s.Remove(ctx, tower))
	_, err = s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	if !errors.Is(err, ErrStale) {
		t.Fatal("source deletion did not invalidate cursor")
	}
}
