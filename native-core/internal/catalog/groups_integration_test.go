//go:build integration

// 分组回归覆盖跨批查询、失败回滚、重启、来源刷新与真实运行状态。
package catalog

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"testing"
)

// TestFlightGroupPersistenceAndPaging验证组计数和分页不受当前加载页限制。
func TestFlightGroupPersistenceAndPaging(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "groups.db")
	s, err := Open(ctx, path)
	requireResult(t, err)
	defer func() { s.Close() }()
	nodes := make([]Node, 205)
	for i := range nodes {
		nodes[i] = reviewParse(t, fmt.Sprintf("proxies: [{name: N%d, type: ss, server: n%d.example, port: 443, cipher: aes-128-gcm, password: fixture}]", i, i))[0]
	}
	reviewImport(t, s, "one", nodes)
	group, err := s.CreateGroup(ctx, "常用")
	requireResult(t, err)
	filter := reviewFilter(t, s, func(FilterInput) bool { return true })
	var ids []int64
	for anchor := int64(0); ; {
		page, e := s.Page(ctx, "flights", anchor, false, filter.Revision, filter.Token)
		requireResult(t, e)
		for _, n := range page.Flights {
			ids = append(ids, n.ID)
		}
		if len(page.Flights) < 100 {
			break
		}
		anchor = page.Flights[len(page.Flights)-1].ID
	}
	requireResult(t, s.MoveGroup(ctx, group.ID, ids))
	if _, err = s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token); !errors.Is(err, ErrStale) {
		t.Fatal("move did not invalidate old page", err)
	}
	filter = reviewScopedFilter(t, s, FlightScope{GroupID: group.ID, Status: "all"}, func(FilterInput) bool { return true })
	page, err := s.Page(ctx, "flights", ids[99], false, filter.Revision, filter.Token)
	requireResult(t, err)
	if page.MatchCount != 205 || len(page.Flights) != 100 {
		t.Fatal("group pagination", page.MatchCount, len(page.Flights))
	}
	if err = s.MoveGroup(ctx, 0, []int64{ids[0], 999999}); !errors.Is(err, ErrStale) {
		t.Fatal("missing node accepted", err)
	}
	groups, err := s.Groups(ctx, nil)
	requireResult(t, err)
	if len(groups.Groups) != 1 || groups.Groups[0].Count != 205 {
		t.Fatal("partial move", groups)
	}
	requireResult(t, s.RenameGroup(ctx, group.ID, "工作"))
	requireResult(t, s.Close())
	s, err = Open(ctx, path)
	requireResult(t, err)
	reviewImport(t, s, "one", nodes)
	groups, err = s.Groups(ctx, nil)
	requireResult(t, err)
	if groups.Groups[0].Name != "工作" || groups.Groups[0].Count != 205 {
		t.Fatal("restart or refresh lost membership", groups)
	}
	requireResult(t, s.DeleteGroup(ctx, group.ID))
	groups, err = s.Groups(ctx, nil)
	requireResult(t, err)
	if len(groups.Groups) != 0 || groups.Counts["ungrouped"] != 205 || reviewCount(t, s, "nodes") != 205 {
		t.Fatal("deleting group deleted nodes", groups)
	}
}

// TestFlightGroupStatusScope验证状态、名称和分组交集及结果变更失效。
func TestFlightGroupStatusScope(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	reviewImport(t, s, "one", []Node{reviewNode(t, "A"), reviewNode(t, "B")})
	group, err := s.CreateGroup(ctx, "目标")
	requireResult(t, err)
	node := resultNode(t, s)
	requireResult(t, s.MoveGroup(ctx, group.ID, []int64{node}))
	_, run := startResult(t, s, node, "Latency")
	requireResult(t, s.UpdateRun(ctx, run.ID, "failed", "timeout"))
	filter := reviewScopedFilter(t, s, FlightScope{GroupID: group.ID, Status: "failed"}, func(n FilterInput) bool { return n.Name == "A" })
	page, err := s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token)
	requireResult(t, err)
	if page.MatchCount != 1 || len(page.Flights) != 1 || page.Flights[0].ID != node {
		t.Fatal("scope intersection", page)
	}
	groups, err := s.Groups(ctx, &filter)
	requireResult(t, err)
	if groups.Counts["all"] != 1 || groups.Counts["failed"] != 1 || groups.Groups[0].Count != 1 {
		t.Fatal("search-aware counts", groups)
	}
	_, run = startResult(t, s, node, "Latency")
	if _, err = s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token); err != nil {
		t.Fatal("metrics update invalidated directory cursor", err)
	}
	saveMetric(t, s, run.ID, "latency", "succeeded", 25)
	requireResult(t, s.UpdateRun(ctx, run.ID, "succeeded", ""))
	filter = reviewScopedFilter(t, s, FlightScope{GroupID: -1, Status: "succeeded"}, func(FilterInput) bool { return true })
	page, err = s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token)
	requireResult(t, err)
	if page.MatchCount != 1 {
		t.Fatal("successful run missing", page.MatchCount)
	}
	filter = reviewScopedFilter(t, s, FlightScope{GroupID: 0, Status: "none"}, func(FilterInput) bool { return true })
	page, err = s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token)
	requireResult(t, err)
	if page.MatchCount != 1 || page.Flights[0].ID == node {
		t.Fatal("ungrouped untested scope", page)
	}
}

// TestFlightGroupCollapse保留多名称收敛后的最近一次显式分组归属。
func TestFlightGroupCollapse(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	a, b := reviewNode(t, "A"), reviewNode(t, "B")
	reviewImport(t, s, "one", []Node{a, b})
	group, err := s.CreateGroup(ctx, "收敛后保留")
	requireResult(t, err)
	var last int64
	requireResult(t, s.db.QueryRow("SELECT max(id) FROM nodes").Scan(&last))
	requireResult(t, s.MoveGroup(ctx, group.ID, []int64{last}))
	reviewImport(t, s, "one", []Node{a})
	groups, err := s.Groups(ctx, nil)
	requireResult(t, err)
	if groups.Groups[0].Count != 1 || reviewCount(t, s, "nodes") != 1 {
		t.Fatal("collapse lost membership", groups)
	}
	if _, err = s.CreateGroup(ctx, "收敛后保留"); err == nil {
		t.Fatal("duplicate group accepted")
	}
	if _, err = s.CreateGroup(ctx, " "); err == nil {
		t.Fatal("blank name accepted")
	}
	requireResult(t, s.MoveGroup(ctx, 0, []int64{resultNode(t, s)}))
	groups, err = s.Groups(ctx, nil)
	requireResult(t, err)
	if groups.Counts["ungrouped"] != 1 {
		t.Fatal("ungroup failed", groups)
	}
}
