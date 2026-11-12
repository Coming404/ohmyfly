//go:build integration

// 检查解析引用生命周期、超大记录分页和WAL快照；不做大规模性能压测。
package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"strings"
	"testing"
	"time"
)

// WAL读取必须在写事务未提交时立即取得旧快照，不能等待唯一写连接。
func TestWALReadDuringWrite(t *testing.T) {
	s := reviewStore(t)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	id := reviewImport(t, s, "local:wal", reviewParse(t, "trojan://fixture@node.example:443#old"))
	var mode string
	requireResult(t, s.db.QueryRowContext(ctx, "PRAGMA journal_mode").Scan(&mode))
	if mode != "wal" {
		t.Fatalf("日志模式错误: %s", mode)
	}
	tx, err := s.db.BeginTx(ctx, nil)
	requireResult(t, err)
	defer tx.Rollback()
	_, err = tx.ExecContext(ctx, "UPDATE towers SET name='pending' WHERE id=?", id)
	requireResult(t, err)
	summary, err := s.Summary(ctx)
	requireResult(t, err)
	page, err := s.Page(ctx, "towers", 0, false, summary.Revision, 0)
	requireResult(t, err)
	if len(page.Towers) != 1 || page.Towers[0].Name == "pending" {
		t.Fatal("读取泄漏未提交写入")
	}
	requireResult(t, tx.Commit())
	page, err = s.Page(ctx, "towers", 0, false, summary.Revision, 0)
	requireResult(t, err)
	if page.Towers[0].Name != "pending" {
		t.Fatal("新快照未看到已提交写入")
	}
	if _, err = s.readDB.ExecContext(ctx, "UPDATE towers SET name='bad'"); err == nil {
		t.Fatal("读取连接允许写入")
	}
}

// 写连接被回收重建后，刷新分类仍应可用，不能依赖连接私有TEMP表。
func TestRefreshAfterWriterReconnect(t *testing.T) {
	s := reviewStore(t)
	s.db.SetMaxIdleConns(0)
	reviewImport(t, s, "local:reconnect", reviewParse(t, "trojan://fixture@node.example:443#node"))
	if reviewCount(t, s, "nodes") != 1 {
		t.Fatal("连接重建后提交失败")
	}
	if reviewCount(t, s, "refresh_affected") != 0 {
		t.Fatal("提交后未清理工作集合")
	}
}

// 普通记录按字节分页；超过预算的单节点仍完整返回，引用复制后可以释放解析会话。
func TestParsedReferenceLifetimeAndLargeRecord(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	nodes := reviewParse(t, "trojan://fixture@one.example:443#one\ntrojan://fixture@two.example:443#two\ntrojan://fixture@three.example:443#three")
	nodes[1].RawInput = strings.Repeat("大", ParseBatchBytes/3+1)
	session, err := s.BeginParse(ctx)
	requireResult(t, err)
	writer := s.NewParseWriter(session, "node")
	for _, node := range nodes {
		requireResult(t, writer.Append(ctx, node))
	}
	requireResult(t, writer.Flush(ctx))
	for start := range nodes {
		page, err := s.ParsedPage(ctx, session, "node", start)
		requireResult(t, err)
		if len(page.Records) != 1 {
			t.Fatalf("第%d页边界错误: %d", start, len(page.Records))
		}
		var got Node
		requireResult(t, json.Unmarshal(page.Records[0], &got))
		if got.RawInput != nodes[start].RawInput {
			t.Fatal("大记录未完整保留")
		}
	}
	id, err := s.Add(ctx, "local:refs", "refs")
	requireResult(t, err)
	ticket, err := s.Begin(ctx, id)
	requireResult(t, err)
	refs := []ParsedReference{
		{Session: session, Ordinal: 0, Eligible: false, Source: json.RawMessage(`{"url":"excluded"}`)},
		{Session: session, Ordinal: 1, Eligible: true, Source: json.RawMessage(`{"url":"provider"}`)},
		{Session: session, Ordinal: 2, Eligible: true, Source: json.RawMessage(`{"url":"root"}`)},
	}
	requireResult(t, s.StageParsed(ctx, id, ticket.Generation, 0, refs))
	requireResult(t, s.EndParse(ctx, session))
	requireResult(t, s.Commit(ctx, Refresh{ID: id, Generation: ticket.Generation, Count: 3, Usage: json.RawMessage(`null`), Warnings: json.RawMessage(`[]`)}))
	if reviewCount(t, s, "nodes") != 2 || reviewCount(t, s, "tower_nodes") != 2 {
		t.Fatal("引用路径丢失过滤资格")
	}
	var hash, source, raw string
	requireResult(t, s.db.QueryRow(`SELECT n.content_hash,json_extract(t.source,'$.source.url'),json_extract(t.source,'$.rawInput')
 FROM tower_nodes t JOIN nodes n ON n.id=t.node_id WHERE t.tower_id=? AND t.ordinal=1`, id).Scan(&hash, &source, &raw))
	if hash != nodes[1].ContentHash || source != "provider" || raw != nodes[1].RawInput {
		t.Fatal("可信哈希、来源或原文变化")
	}
	for _, table := range []string{"parse_records", "refresh_nodes", "refresh_origin", "refresh_prepared", "refresh_affected"} {
		if reviewCount(t, s, table) != 0 {
			t.Fatalf("未清理暂存表: %s", table)
		}
	}
}

// 批内任一引用失效时整个批次回滚，不能留下已复制的前半段。
func TestParsedReferenceBatchRollback(t *testing.T) {
	s := reviewStore(t)
	ctx := context.Background()
	session, err := s.BeginParse(ctx)
	requireResult(t, err)
	writer := s.NewParseWriter(session, "node")
	requireResult(t, writer.Append(ctx, reviewParse(t, "trojan://fixture@node.example:443#node")[0]))
	requireResult(t, writer.Flush(ctx))
	id, err := s.Add(ctx, "local:rollback", "rollback")
	requireResult(t, err)
	ticket, err := s.Begin(ctx, id)
	requireResult(t, err)
	err = s.StageParsed(ctx, id, ticket.Generation, 0, []ParsedReference{
		{Session: session, Ordinal: 0, Eligible: true}, {Session: session, Ordinal: 1, Eligible: true},
	})
	if err == nil || err == sql.ErrNoRows {
		t.Fatal("未明确拒绝不完整引用批次")
	}
	if reviewCount(t, s, "refresh_nodes") != 0 || reviewCount(t, s, "refresh_origin") != 0 {
		t.Fatal("失败批次没有原子回滚")
	}
}
