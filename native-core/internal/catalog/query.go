package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"slices"
	"time"
)

type Summary struct {
	FuelReported     bool           `json:"fuelReported"`
	SyncableTowers   int64          `json:"syncableTowers"`
	Trend            []DailySummary `json:"trend"`
	MetricsRevision  int64          `json:"metricsRevision"`
	Revision         int64          `json:"revision"`
	Towers           int64          `json:"towers"`
	Flights          int64          `json:"flights"`
	AvailableFlights int64          `json:"availableFlights"`
	FailedTowers     int64          `json:"failedTowers"`
	UsedTrafficBytes int64          `json:"usedTrafficBytes"`
}

type TowerSummary struct {
	SourceCount        int               `json:"sourceCount"`
	SourceKind         string            `json:"sourceKind"`
	Address            string            `json:"address"`
	AvailableNodeCount int64             `json:"availableNodeCount"`
	LastAttemptAt      *int64            `json:"lastAttemptAt"`
	LastSuccessAt      *int64            `json:"lastSuccessAt"`
	Warnings           json.RawMessage   `json:"warnings"`
	ImportResult       TowerImportResult `json:"importResult"`
	ID                 int64             `json:"id"`
	Name               string            `json:"name"`
	NodeCount          int64             `json:"nodeCount"`
	Failure            string            `json:"failure"`
	Usage              json.RawMessage   `json:"usage"`
}

type FlightSummary struct {
	Enabled bool `json:"enabled"`
	// Region 为中文显示名，RegionCode 单独供国旗查找使用。
	Region       string             `json:"region"`
	RegionCode   string             `json:"regionCode"`
	Measurements MeasurementSummary `json:"measurements"`
	ID           int64              `json:"id"`
	Name         string             `json:"name"`
	Type         string             `json:"type"`
	Server       string             `json:"server"`
	Port         int                `json:"port"`
}

type Page struct {
	MetricsRevision int64           `json:"metricsRevision"`
	Revision        int64           `json:"revision"`
	FilterToken     int64           `json:"filterToken"`
	MatchCount      int64           `json:"matchCount"`
	Towers          []TowerSummary  `json:"towers"`
	Flights         []FlightSummary `json:"flights"`
}

func (s *Store) Summary(ctx context.Context) (Summary, error) {
	var result Summary
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	query, err := candidateQuery(ctx, tx, false)
	if err != nil {
		return result, err
	}
	err = tx.QueryRowContext(ctx, query+`SELECT revision,metrics_revision,
 (SELECT count(*) FROM towers WHERE id NOT IN (SELECT source_id FROM tower_sources)),
 (SELECT count(*) FROM candidates),
 (SELECT count(*) FROM candidates c WHERE coalesce((SELECT r.status FROM node_runs r WHERE r.node_id=c.id ORDER BY r.id DESC LIMIT 1),'')='succeeded'
  AND NOT EXISTS (SELECT 1 FROM node_controls nctl WHERE nctl.node_id=c.id AND nctl.enabled=0)),
 (SELECT count(*) FROM towers t WHERE t.id NOT IN (SELECT source_id FROM tower_sources)
  AND (t.failure<>'' OR (t.address LIKE 'local:merged:%' AND EXISTS(
   SELECT 1 FROM tower_sources s JOIN towers child ON child.id=s.source_id
   WHERE s.parent_id=t.id AND child.failure<>'')))),
 (SELECT coalesce(sum(coalesce(json_extract(usage,'$.upload'),0)+coalesce(json_extract(usage,'$.download'),0)),0)
  FROM towers WHERE id NOT IN (SELECT source_id FROM tower_sources) AND json_valid(usage))
 ,EXISTS(SELECT 1 FROM towers WHERE id NOT IN (SELECT source_id FROM tower_sources) AND json_valid(usage)
 AND (json_extract(usage,'$.upload') IS NOT NULL OR json_extract(usage,'$.download') IS NOT NULL)),
 (SELECT count(DISTINCT coalesce(s.parent_id,t.id)) FROM towers t LEFT JOIN tower_sources s ON s.source_id=t.id
 WHERE coalesce(s.address,t.address) NOT LIKE 'local:%')
 FROM catalog_meta WHERE id=1`).Scan(
		&result.Revision, &result.MetricsRevision, &result.Towers, &result.Flights,
		&result.AvailableFlights, &result.FailedTowers, &result.UsedTrafficBytes, &result.FuelReported, &result.SyncableTowers)
	if err != nil {
		return result, err
	}
	if err = s.readTrend(ctx, tx, time.Now(), &result); err != nil {
		return result, err
	}
	if err = tx.Commit(); err != nil {
		return result, err
	}
	return result, nil
}

func (s *Store) Page(ctx context.Context, kind string, anchor int64, backwards bool, revision, filterToken int64) (Page, error) {
	result := Page{FilterToken: filterToken, Towers: []TowerSummary{}, Flights: []FlightSummary{}}
	if anchor < 0 {
		return result, errors.New("invalid page cursor")
	}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	if err = tx.QueryRowContext(ctx, "SELECT revision,metrics_revision FROM catalog_meta WHERE id=1").Scan(&result.Revision, &result.MetricsRevision); err != nil {
		return result, err
	}
	if revision != result.Revision {
		return result, ErrStale
	}
	order := "> ? ORDER BY id ASC LIMIT 100"
	if backwards {
		order = "< ? ORDER BY id DESC LIMIT 100"
	}
	switch kind {
	case "towers":
		result.Towers, err = towerPage(ctx, tx, anchor, order)
		if backwards {
			slices.Reverse(result.Towers)
		}
	case "flights":
		if err = checkFilter(ctx, tx, FilterTicket{Token: filterToken, Revision: revision}, true); err != nil {
			return result, err
		}
		if err = tx.QueryRowContext(ctx, "SELECT match_count FROM filter_work WHERE id=1").Scan(&result.MatchCount); err != nil {
			return result, err
		}
		result.Flights, err = flightPage(ctx, tx, anchor, order)
		if err == nil {
			err = attachRegions(ctx, tx, result.Flights)
		}
		if err == nil {
			err = attachMeasurements(ctx, tx, result.Flights)
		}
		if backwards {
			slices.Reverse(result.Flights)
		}
	default:
		return result, errors.New("invalid catalog list")
	}
	if err != nil {
		return result, err
	}
	return result, tx.Commit()
}

func towerPage(ctx context.Context, tx *sql.Tx, anchor int64, order string) ([]TowerSummary, error) {
	result := []TowerSummary{}
	rows, err := tx.QueryContext(ctx, `SELECT id,substr(name,1,256),
 CASE WHEN address LIKE 'local:merged:%' THEN '' ELSE coalesce((SELECT address FROM tower_sources WHERE source_id=towers.id),address) END,
 (SELECT count(DISTINCT t.node_id) FROM tower_nodes t WHERE t.tower_id IN
 (SELECT source_id FROM tower_sources WHERE parent_id=towers.id UNION ALL SELECT towers.id) AND t.eligible=1),
 (SELECT count(DISTINCT t.node_id) FROM tower_nodes t LEFT JOIN node_controls c ON c.node_id=t.node_id WHERE t.tower_id IN
 (SELECT source_id FROM tower_sources WHERE parent_id=towers.id UNION ALL SELECT towers.id) AND t.eligible=1
 AND coalesce(c.enabled,1)=1 AND coalesce((SELECT r.status FROM node_runs r WHERE r.node_id=t.node_id ORDER BY r.id DESC LIMIT 1),'')='succeeded'),
 substr(failure,1,512),usage,warnings,last_attempt_at,last_success_at,
 coalesce((SELECT added FROM tower_imports WHERE tower_id=towers.id),0),
 coalesce((SELECT updated FROM tower_imports WHERE tower_id=towers.id),0),
 coalesce((SELECT removed FROM tower_imports WHERE tower_id=towers.id),0),
 coalesce((SELECT failed FROM tower_imports WHERE tower_id=towers.id),0),
 coalesce((SELECT details FROM tower_imports WHERE tower_id=towers.id),'[]'),
 CASE WHEN address LIKE 'local:merged:%' THEN 'merged' WHEN address LIKE 'local:file:%' THEN 'file' WHEN address LIKE 'local:text:%' THEN 'text' ELSE 'http' END
 FROM towers WHERE id NOT IN (SELECT source_id FROM tower_sources) AND id `+order, anchor)
	if err != nil {
		return result, err
	}
	defer rows.Close()
	for rows.Next() {
		var t TowerSummary
		var usage, warnings, importDetails string
		t.ImportResult.Details = []string{}
		if err = rows.Scan(&t.ID, &t.Name, &t.Address, &t.NodeCount, &t.AvailableNodeCount,
			&t.Failure, &usage, &warnings, &t.LastAttemptAt, &t.LastSuccessAt,
			&t.ImportResult.Added, &t.ImportResult.Updated, &t.ImportResult.Removed,
			&t.ImportResult.Failed, &importDetails, &t.SourceKind); err != nil {
			return result, err
		}
		t.Usage = json.RawMessage(usage)
		t.Warnings = json.RawMessage(warnings)
		if err = json.Unmarshal([]byte(importDetails), &t.ImportResult.Details); err != nil {
			return result, err
		}
		result = append(result, t)
	}
	if err = rows.Err(); err != nil {
		return result, err
	}
	if err = rows.Close(); err != nil {
		return result, err
	}
	for i := range result {
		result[i].SourceCount = 1
		if err = sourceOverview(ctx, tx, &result[i]); err != nil {
			return result, err
		}
	}
	return result, nil
}

func flightPage(ctx context.Context, tx *sql.Tx, anchor int64, order string) ([]FlightSummary, error) {
	result := []FlightSummary{}
	query, err := candidateQuery(ctx, tx, true)
	if err != nil {
		return result, err
	}
	rows, err := tx.QueryContext(ctx, query+`SELECT n.id AS id,substr(n.name,1,256),substr(n.type,1,32),substr(n.server,1,256),n.port
 FROM matched c JOIN nodes n ON n.id=c.id WHERE n.id `+order, anchor)
	if err != nil {
		return result, err
	}
	defer rows.Close()
	for rows.Next() {
		var n FlightSummary
		if err = rows.Scan(&n.ID, &n.Name, &n.Type, &n.Server, &n.Port); err != nil {
			return result, err
		}
		result = append(result, n)
	}
	return result, rows.Err()
}

func (s *Store) Detail(ctx context.Context, id int64) (json.RawMessage, error) {
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	var config string
	if err = tx.QueryRowContext(ctx, "SELECT config FROM nodes WHERE id=?", id).Scan(&config); err != nil {
		return nil, err
	}
	var result map[string]json.RawMessage
	if err = json.Unmarshal([]byte(config), &result); err != nil {
		return nil, err
	}
	var group string
	if err = tx.QueryRowContext(ctx, `SELECT coalesce((SELECT json_object('id',g.id,'name',g.name)
 FROM node_groups m JOIN flight_groups g ON g.id=m.group_id WHERE m.node_id=?),'null')`, id).Scan(&group); err != nil {
		return nil, err
	}
	result["group"] = json.RawMessage(group)
	sources, err := detailSources(ctx, tx, id)
	if err != nil {
		return nil, err
	}
	result["sources"], err = json.Marshal(sources)
	if err != nil {
		return nil, err
	}
	encoded, err := json.Marshal(result)
	if err != nil {
		return nil, err
	}
	return encoded, tx.Commit()
}

func detailSources(ctx context.Context, tx *sql.Tx, id int64) ([]json.RawMessage, error) {
	result := []json.RawMessage{}
	rows, err := tx.QueryContext(ctx, dedupCandidates+`SELECT json_object(
 'towerId',coalesce((SELECT parent_id FROM tower_sources WHERE source_id=m.tower_id),m.tower_id),
 'towerName',(SELECT name FROM towers WHERE id=coalesce((SELECT parent_id FROM tower_sources WHERE source_id=m.tower_id),m.tower_id)),
 'sourceId',m.tower_id,'ordinal',m.ordinal,'metadata',json(m.source))
 FROM source_members m WHERE m.representative_id=? ORDER BY m.tower_id,m.ordinal`, id)
	if err != nil {
		return result, err
	}
	defer rows.Close()
	for rows.Next() {
		var source string
		if err = rows.Scan(&source); err != nil {
			return result, err
		}
		result = append(result, json.RawMessage(source))
	}
	return result, rows.Err()
}
