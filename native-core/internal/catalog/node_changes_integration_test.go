//go:build integration

// 核对分组移动后的单归属、旧页面失效与节点删除的真实外键事务。
package catalog

import (
	"context"
	"errors"
	"testing"
)

// TestFlightMoveAndDelete核对同一航班移出原组后再删除，不留下来源引用。
func TestFlightMoveAndDelete(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	nodes := reviewParse(t, `proxies:
  - {name: selected, type: trojan, server: selected.example, port: 443, password: fixture}
  - {name: retained, type: trojan, server: retained.example, port: 443, password: fixture}`)
	reviewImport(t, s, "first", nodes)
	reviewImport(t, s, "second", nodes[:1])
	initial := reviewScopedFilter(t, s, FlightScope{GroupID: 0, Status: "all"}, func(FilterInput) bool { return true })
	initialPage, err := s.Page(ctx, "flights", 0, false, initial.Revision, initial.Token)
	requireResult(t, err)
	if initialPage.MatchCount != 2 {
		t.Fatal("导入航班未进入默认全部分组", initialPage.MatchCount)
	}
	var id int64
	requireResult(t, s.db.QueryRowContext(ctx, "SELECT id FROM nodes WHERE name='selected'").Scan(&id))
	first, err := s.CreateGroup(ctx, "原分组")
	requireResult(t, err)
	second, err := s.CreateGroup(ctx, "新分组")
	requireResult(t, err)
	requireResult(t, s.MoveGroup(ctx, first.ID, []int64{id}))
	old := reviewScopedFilter(t, s, FlightScope{GroupID: first.ID, Status: "all"}, func(FilterInput) bool { return true })
	requireResult(t, s.MoveGroup(ctx, second.ID, []int64{id}))
	if _, err = s.Page(ctx, "flights", 0, false, old.Revision, old.Token); !errors.Is(err, ErrStale) {
		t.Fatal("移动后旧分组页面仍有效", err)
	}
	for group, want := range map[int64]int64{0: 1, first.ID: 0, second.ID: 1} {
		filter := reviewScopedFilter(t, s, FlightScope{GroupID: group, Status: "all"}, func(FilterInput) bool { return true })
		page, err := s.Page(ctx, "flights", 0, false, filter.Revision, filter.Token)
		requireResult(t, err)
		if page.MatchCount != want || int64(len(page.Flights)) != want {
			t.Fatalf("分组单归属不匹配：期望 %d，得到 %d/%d", want, page.MatchCount, len(page.Flights))
		}
		groups, err := s.Groups(ctx, &filter)
		requireResult(t, err)
		if groups.Counts["all"] != 2 || groups.Counts["default"] != 1 || groups.Counts["none"] != 2 ||
			groups.Counts["failed"] != 0 || groups.Groups[0].Count != 0 || groups.Groups[1].Count != 1 {
			t.Fatal("切换分组改变了其他胶囊数量", groups)
		}
	}
	// 失效选择必须整体拒绝，不能先解除有效节点的来源。
	if err = s.ChangeNodes(ctx, []int64{id, 999999}, "delete"); !errors.Is(err, ErrStale) {
		t.Fatal("删除失效选择未整体拒绝", err)
	}
	if reviewCount(t, s, "tower_nodes") != 3 {
		t.Fatal("失败删除改变了来源关联")
	}
	requireResult(t, s.ChangeNodes(ctx, []int64{id}, "delete"))
	if reviewCount(t, s, "nodes") != 1 || reviewCount(t, s, "tower_nodes") != 1 || reviewCount(t, s, "node_groups") != 0 {
		t.Fatal("删除没有移除所有来源及分组，或破坏了未选航班")
	}
	groups, err := s.Groups(ctx, nil)
	requireResult(t, err)
	if groups.Counts["all"] != 1 || groups.Groups[0].Count != 0 || groups.Groups[1].Count != 0 {
		t.Fatal("删除后汇总或分组计数未更新")
	}
}
