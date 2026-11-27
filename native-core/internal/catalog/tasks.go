package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"
)

type Task struct {
	ID         int64           `json:"id"`
	Mode       string          `json:"mode"`
	Settings   json.RawMessage `json:"settings"`
	Status     string          `json:"status"`
	CreatedAt  int64           `json:"createdAt"`
	StartedAt  *int64          `json:"startedAt"`
	FinishedAt *int64          `json:"finishedAt"`
	Reason     string          `json:"reason"`
}

type NodeRun struct {
	TaskStatus string           `json:"taskStatus"`
	ID         int64            `json:"id"`
	TaskID     int64            `json:"taskId"`
	NodeID     int64            `json:"nodeId"`
	Status     string           `json:"status"`
	StartedAt  *int64           `json:"startedAt"`
	FinishedAt *int64           `json:"finishedAt"`
	Reason     string           `json:"reason"`
	Metrics    []Metric         `json:"metrics"`
	Platforms  []PlatformResult `json:"platforms"`
}

func bumpMetrics(ctx context.Context, tx *sql.Tx) error {
	_, err := tx.ExecContext(ctx, "UPDATE catalog_meta SET metrics_revision=metrics_revision+1 WHERE id=1")
	return err
}

func (s *Store) CreateTask(ctx context.Context, mode string, settings json.RawMessage, nodes []int64) (Task, error) {
	var task Task
	if mode != "Latency" && mode != "Download" && mode != "Full" {
		return task, errors.New("invalid task mode")
	}
	var snapshot map[string]json.RawMessage
	if len(settings) > 65536 || json.Unmarshal(settings, &snapshot) != nil || len(snapshot) == 0 {
		return task, errors.New("invalid settings snapshot")
	}
	var snapshotMode string
	if json.Unmarshal(snapshot["mode"], &snapshotMode) != nil || snapshotMode != mode {
		return task, errors.New("snapshot mode mismatch")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return task, err
	}
	defer tx.Rollback()
	task = Task{Mode: mode, Settings: settings, Status: "queued", CreatedAt: time.Now().UnixMilli()}
	err = tx.QueryRowContext(ctx, `INSERT INTO flight_tasks(mode,settings,status,created_at)
 VALUES(?,?,'queued',?) RETURNING id`, mode, string(settings), task.CreatedAt).Scan(&task.ID)
	if err != nil {
		return task, err
	}
	if err = addTaskNodes(ctx, tx, task.ID, nodes); err != nil {
		return task, err
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return task, err
	}
	return task, tx.Commit()
}

func addTaskNodes(ctx context.Context, tx *sql.Tx, id int64, nodes []int64) error {
	if len(nodes) == 0 || len(nodes) > 100 {
		return errors.New("task node batch must contain 1..100 IDs")
	}
	var status string
	if err := tx.QueryRowContext(ctx, "SELECT status FROM flight_tasks WHERE id=?", id).Scan(&status); err != nil {
		return err
	}
	if status != "queued" {
		return errors.New("task is not queued")
	}
	for _, node := range nodes {
		var exists bool
		if err := tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM node_runs WHERE task_id=? AND node_id=?)", id, node).Scan(&exists); err != nil {
			return err
		}
		if exists {
			return errors.New("duplicate task node")
		}
		result, err := tx.ExecContext(ctx, `INSERT INTO node_runs(task_id,node_id,status)
 SELECT ?,n.id,'queued' FROM nodes n WHERE n.id=?
 AND NOT EXISTS(SELECT 1 FROM node_controls c WHERE c.node_id=n.id AND c.enabled=0) AND `+effectiveNodes, id, node)
		if err != nil {
			return err
		}
		if err = changed(result); err != nil {
			return err
		}
	}
	return nil
}

func (s *Store) AddTaskNodes(ctx context.Context, id int64, nodes []int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = addTaskNodes(ctx, tx, id, nodes); err != nil {
		return err
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func changed(result sql.Result) error {
	n, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if n != 1 {
		return errors.New("invalid result identity or state transition")
	}
	return nil
}

func (s *Store) Task(ctx context.Context, id int64) (Task, error) {
	var task Task
	var settings string
	err := s.readDB.QueryRowContext(ctx, `SELECT id,mode,settings,status,created_at,started_at,finished_at,reason
 FROM flight_tasks WHERE id=?`, id).Scan(&task.ID, &task.Mode, &settings, &task.Status, &task.CreatedAt, &task.StartedAt, &task.FinishedAt, &task.Reason)
	task.Settings = json.RawMessage(settings)
	return task, err
}
