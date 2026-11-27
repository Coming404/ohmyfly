//go:build integration

// 写事务保持未提交时，交互读取必须取得已提交快照；导入让行不能占住写连接。
package catalog

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestInteractiveReadsDuringImportWrite(t *testing.T) {
	s := reviewStore(t)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	id := reviewImport(t, s, "local:interactive", reviewParse(t, "trojan://fixture@node.example:443#old"))
	node := resultNode(t, s)
	task, _ := startResult(t, s, node, "Latency")
	summary, err := s.Summary(ctx)
	requireResult(t, err)
	ticket, err := s.BeginFilter(ctx, summary.Revision, FlightScope{Status: "all"})
	requireResult(t, err)
	tx, err := s.db.BeginTx(ctx, nil)
	requireResult(t, err)
	defer tx.Rollback()
	_, err = tx.ExecContext(ctx, "UPDATE towers SET name='uncommitted' WHERE id=?", id)
	requireResult(t, err)
	inputs, err := s.FilterInputs(ctx, ticket, 0)
	requireResult(t, err)
	if len(inputs) != 1 {
		t.Fatal("筛选读取丢失已提交节点")
	}
	_, err = s.Measurements(ctx, []int64{node}, summary.Revision)
	requireResult(t, err)
	_, err = s.Results(ctx, node, 0, 0, 20)
	requireResult(t, err)
	_, err = s.Task(ctx, task.ID)
	requireResult(t, err)
	_, err = s.RefreshStatus(ctx, id)
	requireResult(t, err)
	_, err = s.Groups(ctx, nil)
	requireResult(t, err)
}

func TestImportYieldKeepsWriterAvailableAndCanCancel(t *testing.T) {
	s := reviewStore(t)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	finish := s.BeginInteraction()
	cancelled, stop := context.WithCancel(ctx)
	stop()
	if err := s.WaitForInteraction(cancelled); !errors.Is(err, context.Canceled) {
		t.Fatalf("取消等待未返回取消错误：%v", err)
	}
	// 让行状态存在时交互仍能真实提交，不能把调度锁当成数据库锁。
	_, err := s.CreateGroup(ctx, "交互分组")
	requireResult(t, err)
	finish()
	requireResult(t, s.WaitForInteraction(ctx))
}
