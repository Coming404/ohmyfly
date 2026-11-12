package catalog

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

func terminalStatus(status string) bool {
	return status == "succeeded" || status == "failed" || status == "cancelled"
}

func (s *Store) UpdateTask(ctx context.Context, id int64, status, reason string) error {
	if (status != "running" && !terminalStatus(status)) || len(reason) > 512 {
		return errors.New("invalid task status or reason")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	now := time.Now().UnixMilli()
	if status == "succeeded" {
		var complete bool
		err = tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM node_runs WHERE task_id=?)
 AND NOT EXISTS(SELECT 1 FROM node_runs WHERE task_id=? AND status<>'succeeded')`, id, id).Scan(&complete)
		if err != nil {
			return err
		}
		if !complete {
			return errors.New("task nodes are not successful")
		}
	}
	query := `UPDATE flight_tasks SET status=?,finished_at=?,reason=? WHERE id=? AND status IN ('queued','running')`
	if status == "running" {
		query = `UPDATE flight_tasks SET status=?,started_at=?,reason=? WHERE id=? AND status='queued'`
	}
	if status == "succeeded" {
		query = `UPDATE flight_tasks SET status=?,finished_at=?,reason=? WHERE id=? AND status='running'`
	}
	result, err := tx.ExecContext(ctx, query, status, now, reason, id)
	if err != nil {
		return err
	}
	if err = changed(result); err != nil {
		return err
	}
	if status == "failed" || status == "cancelled" {
		if err = stopTaskRuns(ctx, tx, id, status, reason, now); err != nil {
			return err
		}
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func stopTaskRuns(ctx context.Context, tx *sql.Tx, id int64, status, reason string, now int64) error {
	for _, query := range []string{
		`UPDATE metric_results SET status=?,measured_at=?,reason=? WHERE status='running' AND run_id IN (SELECT id FROM node_runs WHERE task_id=?)`,
		`UPDATE platform_results SET status=?,checked_at=?,reason=? WHERE status='running' AND run_id IN (SELECT id FROM node_runs WHERE task_id=?)`,
		`UPDATE node_runs SET status=?,finished_at=?,reason=? WHERE status IN ('queued','running') AND task_id=?`,
	} {
		if _, err := tx.ExecContext(ctx, query, status, now, reason, id); err != nil {
			return err
		}
	}
	return nil
}

func (s *Store) UpdateRun(ctx context.Context, id int64, status, reason string) error {
	if (status != "running" && !terminalStatus(status)) || len(reason) > 512 {
		return errors.New("invalid node status or reason")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if status == "succeeded" {
		var complete bool
		err = tx.QueryRowContext(ctx, `SELECT
 (SELECT count(*) FROM metric_results WHERE run_id=r.id AND status='succeeded')=
 CASE t.mode WHEN 'Latency' THEN 1 WHEN 'Download' THEN 2 ELSE 3 END
 AND NOT EXISTS(SELECT 1 FROM platform_results WHERE run_id=r.id AND status NOT IN ('unlocked','locked'))
 FROM node_runs r JOIN flight_tasks t ON t.id=r.task_id WHERE r.id=?`, id).Scan(&complete)
		if err != nil {
			return err
		}
		if !complete {
			return errors.New("node measurements are incomplete")
		}
	}
	now := time.Now().UnixMilli()
	query := `UPDATE node_runs SET status=?,finished_at=?,reason=? WHERE id=? AND status='running'
 AND EXISTS(SELECT 1 FROM flight_tasks t WHERE t.id=node_runs.task_id AND t.status='running')`
	if status == "running" {
		query = `UPDATE node_runs SET status=?,started_at=?,reason=? WHERE id=? AND status='queued'
 AND EXISTS(SELECT 1 FROM flight_tasks t WHERE t.id=node_runs.task_id AND t.status='running')`
	}
	result, err := tx.ExecContext(ctx, query, status, now, reason, id)
	if err != nil {
		return err
	}
	if err = changed(result); err != nil {
		return err
	}
	if status == "failed" || status == "cancelled" {
		for _, query := range []string{
			"UPDATE metric_results SET status=?,measured_at=?,reason=? WHERE run_id=? AND status='running'",
			"UPDATE platform_results SET status=?,checked_at=?,reason=? WHERE run_id=? AND status='running'",
		} {
			if _, err = tx.ExecContext(ctx, query, status, now, reason, id); err != nil {
				return err
			}
		}
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func interruptTasks(ctx context.Context, tx *sql.Tx) error {
	now := time.Now().UnixMilli()
	// 一次恢复全部未完成阶段；已成功的指标及其真实测量时间不动。
	for _, query := range []string{
		`UPDATE metric_results SET status='interrupted',measured_at=?,reason='application restarted' WHERE status='running'`,
		`UPDATE platform_results SET status='interrupted',checked_at=?,reason='application restarted' WHERE status='running'`,
		`UPDATE node_runs SET status='interrupted',finished_at=?,reason='application restarted' WHERE status IN ('queued','running')`,
	} {
		if _, err := tx.ExecContext(ctx, query, now); err != nil {
			return err
		}
	}
	result, err := tx.ExecContext(ctx, `UPDATE flight_tasks SET status='interrupted',finished_at=?,reason='application restarted'
 WHERE status IN ('queued','running')`, now)
	if err != nil {
		return err
	}
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count > 0 {
		return bumpMetrics(ctx, tx)
	}
	return nil
}
