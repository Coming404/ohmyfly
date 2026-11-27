package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"math"
)

type ResultPage struct {
	MetricsRevision int64     `json:"metricsRevision"`
	Runs            []NodeRun `json:"runs"`
}

// 节点历史或任务成员共用稳定 run ID，单次最多读取100条。
func (s *Store) Results(ctx context.Context, nodeID, taskID, before int64, limit int) (ResultPage, error) {
	result := ResultPage{Runs: []NodeRun{}}
	if nodeID < 0 || taskID < 0 || (nodeID <= 0) == (taskID <= 0) || before < 0 || limit < 1 || limit > 100 {
		return result, errors.New("invalid results cursor")
	}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	if err = tx.QueryRowContext(ctx, "SELECT metrics_revision FROM catalog_meta WHERE id=1").Scan(&result.MetricsRevision); err != nil {
		return result, err
	}
	column, id := "node_id", nodeID
	if taskID > 0 {
		column, id = "task_id", taskID
	}
	var exists bool
	parent := "nodes"
	if taskID > 0 {
		parent = "flight_tasks"
	}
	if err = tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM "+parent+" WHERE id=?)", id).Scan(&exists); err != nil {
		return result, err
	}
	if !exists {
		return result, sql.ErrNoRows
	}
	if before == 0 {
		before = math.MaxInt64
	}
	rows, err := tx.QueryContext(ctx, `SELECT r.id,r.task_id,r.node_id,r.status,r.started_at,r.finished_at,r.reason,t.status
 FROM node_runs r JOIN flight_tasks t ON t.id=r.task_id WHERE r.`+column+`=? AND r.id<? ORDER BY r.id DESC LIMIT ?`, id, before, limit)
	if err != nil {
		return result, err
	}
	for rows.Next() {
		var run NodeRun
		run.Metrics = []Metric{}
		run.Platforms = []PlatformResult{}
		if err = rows.Scan(&run.ID, &run.TaskID, &run.NodeID, &run.Status, &run.StartedAt, &run.FinishedAt, &run.Reason, &run.TaskStatus); err != nil {
			rows.Close()
			return result, err
		}
		result.Runs = append(result.Runs, run)
	}
	err = errors.Join(rows.Err(), rows.Close())
	if err != nil {
		return result, err
	}
	if err = loadRunResults(ctx, tx, result.Runs); err != nil {
		return result, err
	}
	return result, tx.Commit()
}

func loadRunResults(ctx context.Context, tx *sql.Tx, runs []NodeRun) error {
	ids := make([]int64, len(runs))
	index := make(map[int64]int, len(runs))
	for i, run := range runs {
		ids[i] = run.ID
		index[run.ID] = i
	}
	encoded, err := json.Marshal(ids)
	if err != nil {
		return err
	}
	rows, err := tx.QueryContext(ctx, `SELECT run_id,kind,status,value,measured_at,reason
 FROM metric_results WHERE run_id IN (SELECT value FROM json_each(?)) ORDER BY run_id,kind`, string(encoded))
	if err != nil {
		return err
	}
	for rows.Next() {
		var metric Metric
		if err = rows.Scan(&metric.RunID, &metric.Kind, &metric.Status, &metric.Value, &metric.MeasuredAt, &metric.Reason); err != nil {
			rows.Close()
			return err
		}
		i := index[metric.RunID]
		runs[i].Metrics = append(runs[i].Metrics, metric)
	}
	err = errors.Join(rows.Err(), rows.Close())
	if err != nil {
		return err
	}
	rows, err = tx.QueryContext(ctx, `SELECT run_id,platform,status,region,checked_at,reason
 FROM platform_results WHERE run_id IN (SELECT value FROM json_each(?)) ORDER BY run_id,platform`, string(encoded))
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var platform PlatformResult
		if err = rows.Scan(&platform.RunID, &platform.Platform, &platform.Status, &platform.Region, &platform.CheckedAt, &platform.Reason); err != nil {
			return err
		}
		i := index[platform.RunID]
		runs[i].Platforms = append(runs[i].Platforms, platform)
	}
	return rows.Err()
}
