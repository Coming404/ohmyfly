//go:build integration

// 地区规则升级使用真实 SQLite，验证跨批回填、手动值保留、失败回滚和一次性执行。
package catalog

import (
	"context"
	"fmt"
	"testing"
)

// TestRegionRulesUpgrade 验证已有地区表的 v1 库升级，而非只覆盖首次建表。
func TestRegionRulesUpgrade(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	nodes := make([]Node, 103)
	for i := range nodes {
		nodes[i] = reviewParse(t, fmt.Sprintf(
			"proxies: [{name: Tokyo%03d, type: trojan, server: node%d.example, port: 443, password: fixture}]", i, i,
		))[0]
	}
	reviewImport(t, s, "upgrade", nodes)
	_, err := s.db.ExecContext(ctx, `UPDATE node_regions SET name_region='',manual_region='SG'; PRAGMA user_version=1`)
	requireResult(t, err)
	requireResult(t, s.migrate(ctx))
	var version, count int
	requireResult(t, s.db.QueryRowContext(ctx, "PRAGMA user_version").Scan(&version))
	requireResult(t, s.db.QueryRowContext(ctx,
		"SELECT count(*) FROM node_regions WHERE name_region='JP' AND manual_region='SG'",
	).Scan(&count))
	if version != 2 || count != len(nodes) {
		t.Fatalf("version=%d, preserved and identified=%d", version, count)
	}
	// 第二次初始化不应重新写规则目录或重识别节点，触发器会将误写暴露为错误。
	_, err = s.db.ExecContext(ctx, `
CREATE TRIGGER reject_region_catalog_rewrite BEFORE INSERT ON region_catalog
BEGIN SELECT RAISE(ABORT, 'unexpected catalog rewrite'); END;
CREATE TRIGGER reject_region_rewrite BEFORE INSERT ON node_regions
BEGIN SELECT RAISE(ABORT, 'unexpected region rewrite'); END;`)
	requireResult(t, err)
	requireResult(t, s.migrate(ctx))
}

// TestRegionRulesUpgradeRollback 验证后续节点失败时，已写入的名称结果及版本标记一起回滚。
func TestRegionRulesUpgradeRollback(t *testing.T) {
	ctx := context.Background()
	s := reviewStore(t)
	nodes := []Node{reviewNode(t, "Tokyo"), reviewNode(t, "Osaka")}
	reviewImport(t, s, "rollback", nodes)
	_, err := s.db.ExecContext(ctx, `
UPDATE node_regions SET name_region='',manual_region='SG';
PRAGMA user_version=1;
CREATE TRIGGER fail_region_upgrade BEFORE UPDATE ON node_regions
WHEN OLD.node_id=(SELECT max(id) FROM nodes)
BEGIN SELECT RAISE(ABORT, 'region write failed'); END;`)
	requireResult(t, err)
	if err = s.migrate(ctx); err == nil {
		t.Fatal("migration accepted a failed region update")
	}
	var version, count int
	requireResult(t, s.db.QueryRowContext(ctx, "PRAGMA user_version").Scan(&version))
	requireResult(t, s.db.QueryRowContext(ctx,
		"SELECT count(*) FROM node_regions WHERE name_region='' AND manual_region='SG'",
	).Scan(&count))
	if version != 1 || count != len(nodes) {
		t.Fatalf("failed migration persisted partial changes: version=%d, old rows=%d", version, count)
	}
	_, err = s.db.ExecContext(ctx, "DROP TRIGGER fail_region_upgrade")
	requireResult(t, err)
	requireResult(t, s.migrate(ctx))
}
