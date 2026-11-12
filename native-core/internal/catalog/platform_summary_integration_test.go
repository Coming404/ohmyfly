//go:build integration

package catalog

import (
	"context"
	"testing"
	"time"
)

func TestPlatformSummariesKeepLatestIdentityAndHistory(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	reviewImport(t, s, "one", []Node{reviewNode(t, "A"), reviewNode(t, "B")})
	ticket := reviewFilter(t, s, func(FilterInput) bool { return true })
	page, err := s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	requireResult(t, err)
	ids := []int64{page.Flights[1].ID, page.Flights[0].ID}
	task, run := startResult(t, s, ids[0], "Latency")
	now := time.Now().UnixMilli()
	for _, platform := range []PlatformResult{
		{RunID: run.ID, Platform: "custom_stream", Status: "unlocked", Region: "JP", CheckedAt: &now},
		{RunID: run.ID, Platform: "another-service", Status: "locked", Region: "US", CheckedAt: &now},
	} {
		requireResult(t, s.WritePlatform(ctx, platform))
	}
	requireResult(t, s.UpdateTask(ctx, task.ID, "failed", "test"))
	_, newer := startResult(t, s, ids[0], "Latency")
	checked := time.Now().UnixMilli()
	requireResult(t, s.WritePlatform(ctx, PlatformResult{
		RunID: newer.ID, Platform: "custom_stream", Status: "failed", CheckedAt: &checked, Reason: "timeout",
	}))
	batch, err := s.Measurements(ctx, ids, ticket.Revision)
	requireResult(t, err)
	platforms := batch.Nodes[0].Platforms
	if batch.Nodes[0].NodeID != ids[0] || batch.Nodes[1].NodeID != ids[1] || len(batch.Nodes[1].Platforms) != 0 {
		t.Fatal("batch identity or untested platform absence lost")
	}
	if len(platforms) != 2 || platforms[0].Platform != "another-service" || platforms[0].Region != "US" ||
		platforms[1].Platform != "custom_stream" || platforms[1].Status != "failed" || platforms[1].Region != "" ||
		platforms[1].CheckedAt == nil || *platforms[1].CheckedAt != checked {
		t.Fatalf("latest platform facts lost: %+v", platforms)
	}
	history, err := s.Results(ctx, ids[0], 0, newer.ID, 100)
	requireResult(t, err)
	if len(history.Runs) != 1 || history.Runs[0].ID != run.ID || len(history.Runs[0].Platforms) != 2 ||
		history.Runs[0].Platforms[1].Region != "JP" || history.Runs[0].Platforms[1].Status != "unlocked" {
		t.Fatal("summary overwrote full historical platform results")
	}
	page, err = s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	requireResult(t, err)
	if page.MetricsRevision != batch.MetricsRevision || len(page.Flights[1].Measurements.Platforms) != 2 {
		t.Fatal("page and batched measurements disagree")
	}
}
