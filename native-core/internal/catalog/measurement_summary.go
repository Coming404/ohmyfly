package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
)

type MetricSummary struct {
	Latest *Metric `json:"latest"`
	Valid  *Metric `json:"valid"`
}

type PlatformSummary struct {
	Platform  string `json:"platform"`
	Status    string `json:"status"`
	Region    string `json:"region"`
	CheckedAt *int64 `json:"checkedAt"`
}

type MeasurementSummary struct {
	NodeID    int64                    `json:"nodeId"`
	LatestRun *NodeRun                 `json:"latestRun"`
	Metrics   map[string]MetricSummary `json:"metrics"`
	Platforms []PlatformSummary        `json:"platforms"`
}

type Measurements struct {
	Revision        int64                `json:"revision"`
	MetricsRevision int64                `json:"metricsRevision"`
	Nodes           []MeasurementSummary `json:"nodes"`
}

func (s *Store) Measurements(ctx context.Context, nodes []int64, revision int64) (Measurements, error) {
	result := Measurements{Nodes: []MeasurementSummary{}}
	if len(nodes) > 100 {
		return result, errors.New("measurement batch exceeds 100 nodes")
	}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	err = tx.QueryRowContext(ctx, "SELECT revision,metrics_revision FROM catalog_meta WHERE id=1").Scan(&result.Revision, &result.MetricsRevision)
	if err != nil {
		return result, err
	}
	if revision != result.Revision {
		return result, ErrStale
	}
	encoded, err := json.Marshal(nodes)
	if err != nil {
		return result, err
	}
	var count int
	if err = tx.QueryRowContext(ctx, "SELECT count(*) FROM nodes WHERE id IN (SELECT value FROM json_each(?))", string(encoded)).Scan(&count); err != nil {
		return result, err
	}
	if count != len(nodes) {
		return result, errors.New("unknown or duplicate measurement node")
	}
	result.Nodes, err = measurementSummaries(ctx, tx, nodes)
	if err != nil {
		return result, err
	}
	return result, tx.Commit()
}

func measurementSummaries(ctx context.Context, tx *sql.Tx, ids []int64) ([]MeasurementSummary, error) {
	result := make([]MeasurementSummary, len(ids))
	index := make(map[int64]int, len(ids))
	for i, id := range ids {
		if id <= 0 {
			return nil, errors.New("invalid node ID")
		}
		if _, exists := index[id]; exists {
			return nil, errors.New("duplicate node ID")
		}
		index[id] = i
		result[i] = MeasurementSummary{NodeID: id, Metrics: map[string]MetricSummary{}, Platforms: []PlatformSummary{}}
	}
	encoded, err := json.Marshal(ids)
	if err != nil {
		return nil, err
	}
	rows, err := tx.QueryContext(ctx, `SELECT r.id,r.task_id,r.node_id,r.status,r.started_at,r.finished_at,r.reason,t.status
 FROM nodes n JOIN node_runs r ON r.id=(SELECT id FROM node_runs WHERE node_id=n.id ORDER BY id DESC LIMIT 1)
 JOIN flight_tasks t ON t.id=r.task_id
 WHERE n.id IN (SELECT value FROM json_each(?))`, string(encoded))
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		run := NodeRun{Metrics: []Metric{}, Platforms: []PlatformResult{}}
		if err = rows.Scan(&run.ID, &run.TaskID, &run.NodeID, &run.Status, &run.StartedAt, &run.FinishedAt, &run.Reason, &run.TaskStatus); err != nil {
			rows.Close()
			return nil, err
		}
		result[index[run.NodeID]].LatestRun = &run
	}
	err = errors.Join(rows.Err(), rows.Close())
	if err != nil {
		return nil, err
	}
	if err = summaryMetrics(ctx, tx, string(encoded), result, index); err != nil {
		return nil, err
	}
	if err = summaryPlatforms(ctx, tx, string(encoded), result, index); err != nil {
		return nil, err
	}
	return result, nil
}

func summaryMetrics(ctx context.Context, tx *sql.Tx, ids string, result []MeasurementSummary, index map[int64]int) error {
	rows, err := tx.QueryContext(ctx, `WITH ranked AS (
 SELECT r.node_id,m.*,row_number() OVER(PARTITION BY r.node_id,m.kind
 ORDER BY coalesce(m.measured_at,r.started_at) DESC,r.id DESC) AS latest,
 row_number() OVER(PARTITION BY r.node_id,m.kind,m.status
 ORDER BY m.measured_at DESC,r.id DESC) AS valid
 FROM node_runs r JOIN metric_results m ON m.run_id=r.id
 WHERE r.node_id IN (SELECT value FROM json_each(?)))
 SELECT node_id,run_id,kind,status,value,measured_at,reason,latest FROM ranked
 WHERE latest=1 OR (status='succeeded' AND valid=1)`, ids)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var nodeID, latest int64
		var metric Metric
		if err = rows.Scan(&nodeID, &metric.RunID, &metric.Kind, &metric.Status, &metric.Value, &metric.MeasuredAt, &metric.Reason, &latest); err != nil {
			return err
		}
		i := index[nodeID]
		summary := result[i].Metrics[metric.Kind]
		if latest == 1 {
			summary.Latest = &metric
		}
		if metric.Status == "succeeded" {
			summary.Valid = &metric
		}
		result[i].Metrics[metric.Kind] = summary
	}
	return rows.Err()
}

func summaryPlatforms(ctx context.Context, tx *sql.Tx, ids string, result []MeasurementSummary, index map[int64]int) error {
	rows, err := tx.QueryContext(ctx, `WITH ranked AS (
 SELECT r.node_id,p.platform,p.status,p.region,p.checked_at,row_number() OVER(PARTITION BY r.node_id,p.platform
 ORDER BY coalesce(p.checked_at,r.started_at) DESC,r.id DESC) AS latest
 FROM node_runs r JOIN platform_results p ON p.run_id=r.id
 WHERE r.node_id IN (SELECT value FROM json_each(?)))
 SELECT node_id,platform,status,region,checked_at FROM ranked WHERE latest=1 ORDER BY node_id,platform`, ids)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var nodeID int64
		var platform PlatformSummary
		if err = rows.Scan(&nodeID, &platform.Platform, &platform.Status, &platform.Region, &platform.CheckedAt); err != nil {
			return err
		}
		i := index[nodeID]
		result[i].Platforms = append(result[i].Platforms, platform)
	}
	return rows.Err()
}

func attachMeasurements(ctx context.Context, tx *sql.Tx, flights []FlightSummary) error {
	ids := make([]int64, len(flights))
	for i, flight := range flights {
		ids[i] = flight.ID
	}
	summaries, err := measurementSummaries(ctx, tx, ids)
	if err != nil {
		return err
	}
	for i := range flights {
		flights[i].Measurements = summaries[i]
	}
	return nil
}
