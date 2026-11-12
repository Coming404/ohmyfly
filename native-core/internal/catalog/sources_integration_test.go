//go:build integration

// 验证保留独立来源的合并、并发提交、失败保留与级联删除。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"sync"
	"testing"
)

func commitSource(t *testing.T, s *Store, id int64, nodes []Node) {
	t.Helper()
	ticket, err := s.Begin(t.Context(), id)
	if err != nil {
		t.Fatal(err)
	}
	if err = s.Stage(t.Context(), id, ticket.Generation, 0, nodes); err != nil {
		t.Fatal(err)
	}
	if err = s.Commit(t.Context(), Refresh{ID: id, Generation: ticket.Generation, Count: len(nodes), Usage: json.RawMessage(`null`), Warnings: json.RawMessage(`[]`)}); err != nil {
		t.Fatal(err)
	}
}

func TestMergedSourcesLifecycle(t *testing.T) {
	s := reviewStore(t)
	ctx := t.Context()
	nodes := reviewParse(t, "trojan://fixture@a.example:443#A\ntrojan://fixture@b.example:443#B")
	standalone := reviewImport(t, s, "https://a.example/sub", nodes)
	sources, err := s.AddSources(ctx, []SourceInput{{Address: "https://a.example/sub"}, {Address: "https://b.example/sub"}}, true, "合并", 0)
	if err != nil {
		t.Fatal(err)
	}
	parent := sources[0].ParentID
	local, err := s.AddSources(ctx, []SourceInput{{Address: "local:file:fixture", Name: "文件"}}, true, "合并", parent)
	if err != nil {
		t.Fatal(err)
	}
	sources = append(sources, local...)
	var wg sync.WaitGroup
	for _, source := range sources {
		wg.Go(func() { commitSource(t, s, source.ID, nodes) })
	}
	wg.Wait()
	summary, err := s.Summary(ctx)
	if err != nil || summary.Towers != 2 || summary.Flights != 2 {
		t.Fatalf("合并统计错误: %+v %v", summary, err)
	}
	page, err := s.Page(ctx, "towers", 0, false, summary.Revision, 0)
	if err != nil || len(page.Towers) != 2 || page.Towers[1].SourceKind != "merged" || page.Towers[1].SourceCount != 3 || page.Towers[1].NodeCount != 2 {
		t.Fatalf("可见塔台错误: %+v %v", page.Towers, err)
	}
	flights, err := s.TowerFlights(ctx, parent, 0, summary.Revision)
	if err != nil || flights.MatchCount != 2 {
		t.Fatalf("合并节点未去重: %v", err)
	}
	ticket, err := s.Begin(ctx, sources[0].ID)
	if err != nil || ticket.Address != "https://a.example/sub" {
		t.Fatalf("来源地址丢失: %v", err)
	}
	if err = s.Fail(ctx, ticket.ID, ticket.Generation, "失败", " "); err != nil {
		t.Fatal(err)
	}
	current, _ := s.Summary(ctx)
	kept, err := s.TowerFlights(ctx, parent, 0, current.Revision)
	if err != nil || kept.MatchCount != 2 {
		t.Fatal("失败清除了旧来源快照")
	}
	ticket, err = s.Begin(ctx, local[0].ID)
	if err != nil {
		t.Fatal(err)
	}
	if err = s.Remove(ctx, parent); err != nil {
		t.Fatal(err)
	}
	if err = s.Stage(ctx, ticket.ID, ticket.Generation, 0, nodes); !errors.Is(err, ErrStale) {
		t.Fatalf("删除后接受迟到来源: %v", err)
	}
	if err = s.Commit(ctx, Refresh{ID: ticket.ID, Generation: ticket.Generation}); !errors.Is(err, ErrStale) {
		t.Fatalf("删除后接受迟到提交: %v", err)
	}
	current, _ = s.Summary(ctx)
	if current.Towers != 1 || current.Flights != 2 {
		t.Fatal("删除合并塔台损坏独立来源")
	}
	remaining, err := s.Sources(ctx, 0, 0)
	if err != nil || len(remaining) != 1 || remaining[0].ID != standalone {
		t.Fatal("合并子来源未清除")
	}
}

func TestMergedSourcesRestartAndPagination(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "catalog.db")
	s, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { s.Close() })
	var parent int64
	for i := range 103 {
		sources, err := s.AddSources(ctx, []SourceInput{{Address: fmt.Sprintf("local:file:%d", i)}}, true, "合并", parent)
		if err != nil {
			t.Fatal(err)
		}
		parent = sources[0].ParentID
	}
	if err = s.Close(); err != nil {
		t.Fatal(err)
	}
	s, err = Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	first, err := s.Sources(ctx, parent, 0)
	if err != nil || len(first) != 100 {
		t.Fatal("来源首批分页错误")
	}
	last, err := s.Sources(ctx, parent, first[99].ID)
	if err != nil || len(last) != 3 {
		t.Fatal("来源后续分页错误")
	}
	own, err := s.Sources(ctx, last[0].ID, 0)
	if err != nil || len(own) != 1 || own[0].ParentID != parent {
		t.Fatal("本地来源重导身份错误")
	}
	summary, err := s.Summary(ctx)
	if err != nil || summary.Towers != 1 {
		t.Fatal("重启暴露内部来源")
	}
}
