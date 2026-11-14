//go:build integration

// 验证去重之后的筛选、分页、重启以及取消均保持数据库一致。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"testing"
)

func reviewFilter(t *testing.T, s *Store, match func(FilterInput) bool) FilterTicket {
	t.Helper()
	return reviewScopedFilter(t, s, FlightScope{GroupID: -1, Status: "all"}, match)
}

// reviewScopedFilter走真实批量筛选事务，验证分组与匹配条件共同生效。
func reviewScopedFilter(t *testing.T, s *Store, scope FlightScope, match func(FilterInput) bool) FilterTicket {
	t.Helper()
	ctx := context.Background()
	summary, err := s.Summary(ctx)
	if err != nil {
		t.Fatal(err)
	}
	ticket, err := s.BeginFilter(ctx, summary.Revision, scope)
	if err != nil {
		t.Fatal(err)
	}
	var after int64
	for {
		batch, err := s.FilterInputs(ctx, ticket, after)
		if err != nil {
			t.Fatal(err)
		}
		if len(batch) == 0 {
			break
		}
		decisions := make([]FilterDecision, len(batch))
		for i, item := range batch {
			decisions[i] = FilterDecision{ID: item.ID, Eligible: match(item)}
		}
		if err = s.StageFilter(ctx, ticket, decisions); err != nil {
			t.Fatal(err)
		}
		after = batch[len(batch)-1].ID
	}
	if _, err = s.CommitFilter(ctx, ticket); err != nil {
		t.Fatal(err)
	}
	return ticket
}

func TestDedupPagination(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	base := reviewNode(t, "A")
	var nodes []Node
	for i := 0; i < 205; i++ {
		n := base
		n.Name = fmt.Sprintf("N%03d", i)
		n.Port = 10000 + i
		var fields map[string]any
		if err := json.Unmarshal(n.Fields, &fields); err != nil {
			t.Fatal(err)
		}
		fields["port"] = n.Port
		n.Fields, _ = json.Marshal(fields)
		nodes = append(nodes, n, n)
	}
	reviewImport(t, s, "paged", nodes)
	if reviewCount(t, s, "nodes") != 205 {
		t.Fatal("分页前未消除重复节点")
	}
	ticket := reviewFilter(t, s, func(FilterInput) bool { return true })
	var after int64
	seen := map[int64]bool{}
	for _, size := range []int{100, 100, 5, 0} {
		p, err := s.Page(ctx, "flights", after, false, ticket.Revision, ticket.Token)
		if err != nil {
			t.Fatal(err)
		}
		if p.MatchCount != 205 || len(p.Flights) != size {
			t.Fatal("分页数量与去重汇总不一致")
		}
		for _, n := range p.Flights {
			if seen[n.ID] {
				t.Fatal("跨页重复")
			}
			seen[n.ID] = true
			after = n.ID
		}
	}
	ticket = reviewFilter(t, s, func(n FilterInput) bool { return n.Name == "N204" })
	p, err := s.Page(ctx, "flights", 0, false, ticket.Revision, ticket.Token)
	if err != nil {
		t.Fatal(err)
	}
	if p.MatchCount != 1 || len(p.Flights) != 1 || p.Flights[0].Name != "N204" {
		t.Fatal("筛选未覆盖深页节点")
	}
	if reviewCount(t, s, "nodes") != 205 || reviewCount(t, s, "tower_nodes") != 410 {
		t.Fatal("筛选修改了入库事实")
	}
	raw, err := s.Detail(ctx, p.Flights[0].ID)
	if err != nil {
		t.Fatal(err)
	}
	var detail struct {
		Sources []json.RawMessage `json:"sources"`
	}
	if err = json.Unmarshal(raw, &detail); err != nil {
		t.Fatal(err)
	}
	if len(detail.Sources) != 2 {
		t.Fatal("详情遗漏重复来源")
	}
}

func TestRefreshRestartAndCancel(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "restart.db")
	s, err := Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	id := reviewImport(t, s, "one", []Node{reviewNode(t, "A")})
	ticket, err := s.Begin(ctx, id)
	if err != nil {
		t.Fatal(err)
	}
	if err = s.Stage(ctx, id, ticket.Generation, 0, []Node{reviewNode(t, "B")}); err != nil {
		t.Fatal(err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if err = s.Commit(cancelled, Refresh{ID: id, Generation: ticket.Generation, Count: 1}); !errors.Is(err, context.Canceled) {
		t.Fatal("取消未传播")
	}
	if err = s.Close(); err != nil {
		t.Fatal(err)
	}
	s, err = Open(ctx, path)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	if reviewCount(t, s, "nodes") != 1 || reviewCount(t, s, "refresh_nodes") != 0 {
		t.Fatal("重启未保留正式节点或未清理暂存")
	}
	if err = s.Commit(ctx, Refresh{ID: id, Generation: ticket.Generation}); !errors.Is(err, ErrStale) {
		t.Fatal("重启后旧票据仍可提交")
	}
	if err = s.Remove(ctx, id); err != nil {
		t.Fatal(err)
	}
	if reviewCount(t, s, "nodes") != 0 {
		t.Fatal("无来源节点未回收")
	}
}
