package catalog

import (
	"context"
	"database/sql"
	"errors"
	"math"
	"regexp"
	"time"
)

type Metric struct {
	RunID      int64    `json:"runId"`
	Kind       string   `json:"kind"`
	Status     string   `json:"status"`
	Value      *float64 `json:"value"`
	MeasuredAt *int64   `json:"measuredAt"`
	Reason     string   `json:"reason"`
}

type PlatformResult struct {
	RunID     int64  `json:"runId"`
	Platform  string `json:"platform"`
	Status    string `json:"status"`
	Region    string `json:"region"`
	CheckedAt *int64 `json:"checkedAt"`
	Reason    string `json:"reason"`
}

func activeRun(ctx context.Context, tx *sql.Tx, id int64) (string, int64, error) {
	var mode string
	var started int64
	err := tx.QueryRowContext(ctx, `SELECT t.mode,r.started_at FROM node_runs r JOIN flight_tasks t ON t.id=r.task_id
 WHERE r.id=? AND r.status='running' AND t.status='running'`, id).Scan(&mode, &started)
	return mode, started, err
}

func validResultTime(status string, at *int64, started int64) bool {
	if status == "running" {
		return at == nil
	}
	return at != nil && *at >= started && *at <= time.Now().UnixMilli()
}

func (s *Store) WriteMetric(ctx context.Context, metric Metric) error {
	if metric.Kind != "latency" && metric.Kind != "download" && metric.Kind != "upload" {
		return errors.New("invalid metric kind")
	}
	if metric.Status != "running" && !terminalStatus(metric.Status) {
		return errors.New("invalid metric status")
	}
	if len(metric.Reason) > 512 {
		return errors.New("metric reason too long")
	}
	if metric.Status == "succeeded" {
		if metric.Value == nil || math.IsNaN(*metric.Value) || math.IsInf(*metric.Value, 0) || *metric.Value < 0 {
			return errors.New("invalid measurement value")
		}
	} else if metric.Value != nil {
		return errors.New("unsuccessful metric must have null value")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	mode, started, err := activeRun(ctx, tx, metric.RunID)
	if err != nil {
		return err
	}
	if (mode == "Latency" && metric.Kind != "latency") || (mode == "Download" && metric.Kind == "upload") {
		return errors.New("metric not included in task mode")
	}
	if !validResultTime(metric.Status, metric.MeasuredAt, started) {
		return errors.New("invalid metric time")
	}
	result, err := tx.ExecContext(ctx, `INSERT INTO metric_results(run_id,kind,status,value,measured_at,reason) VALUES(?,?,?,?,?,?)
 ON CONFLICT(run_id,kind) DO UPDATE SET status=excluded.status,value=excluded.value,measured_at=excluded.measured_at,reason=excluded.reason
 WHERE metric_results.status='running' AND excluded.status<>'running'`,
		metric.RunID, metric.Kind, metric.Status, metric.Value, metric.MeasuredAt, metric.Reason)
	if err != nil {
		return err
	}
	if err = changed(result); err != nil {
		return err
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

var platformID = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]{0,63}$`)

func (s *Store) WritePlatform(ctx context.Context, result PlatformResult) error {
	if !platformID.MatchString(result.Platform) || len(result.Region) > 64 || len(result.Reason) > 512 {
		return errors.New("invalid platform result")
	}
	switch result.Status {
	case "running", "unlocked", "locked", "failed", "cancelled":
	default:
		return errors.New("invalid platform status")
	}
	if result.Status != "unlocked" && result.Status != "locked" && result.Region != "" {
		return errors.New("unsuccessful platform result must have no region")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	_, started, err := activeRun(ctx, tx, result.RunID)
	if err != nil {
		return err
	}
	if !validResultTime(result.Status, result.CheckedAt, started) {
		return errors.New("invalid platform time")
	}
	var full bool
	err = tx.QueryRowContext(ctx, `SELECT count(*)>=32 AND NOT EXISTS(SELECT 1 FROM platform_results WHERE run_id=? AND platform=?)
 FROM platform_results WHERE run_id=?`, result.RunID, result.Platform, result.RunID).Scan(&full)
	if err != nil {
		return err
	}
	if full {
		return errors.New("platform batch limit exceeded")
	}
	updated, err := tx.ExecContext(ctx, `INSERT INTO platform_results(run_id,platform,status,region,checked_at,reason) VALUES(?,?,?,?,?,?)
 ON CONFLICT(run_id,platform) DO UPDATE SET status=excluded.status,region=excluded.region,checked_at=excluded.checked_at,reason=excluded.reason
 WHERE platform_results.status='running' AND excluded.status<>'running'`,
		result.RunID, result.Platform, result.Status, result.Region, result.CheckedAt, result.Reason)
	if err != nil {
		return err
	}
	if err = changed(updated); err != nil {
		return err
	}
	if err = bumpMetrics(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}
