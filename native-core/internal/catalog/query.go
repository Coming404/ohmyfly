package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"slices"
)

type Summary struct {
	Revision int64 `json:"revision"`
	Towers   int64 `json:"towers"`
	Flights  int64 `json:"flights"`
}

type TowerSummary struct {
	ID        int64           `json:"id"`
	Name      string          `json:"name"`
	NodeCount int64           `json:"nodeCount"`
	Failure   string          `json:"failure"`
	Usage     json.RawMessage `json:"usage"`
}

type FlightSummary struct {
	ID     int64  `json:"id"`
	Name   string `json:"name"`
	Type   string `json:"type"`
	Server string `json:"server"`
	Port   int    `json:"port"`
}

type Page struct {
	Revision    int64           `json:"revision"`
	FilterToken int64           `json:"filterToken"`
	MatchCount  int64           `json:"matchCount"`
	Towers      []TowerSummary  `json:"towers"`
	Flights     []FlightSummary `json:"flights"`
}

// 汇总与分页共享同一候选定义：只对已通过过滤的来源按旧配置指纹分组。
const candidates = `WITH candidates AS (
 SELECT min(n.id) AS id FROM nodes n
 WHERE EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id AND t.eligible=1)
 GROUP BY n.legacy_fingerprint
) `

// 先筛选完整有效变体，再按旧指纹分组；代表为匹配变体中的最小 ID。
const filteredCandidates = `WITH candidates AS (
 SELECT min(n.id) AS id FROM nodes n JOIN filter_results r ON r.node_id=n.id AND r.eligible=1
 WHERE EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id AND t.eligible=1)
 GROUP BY n.legacy_fingerprint
) `

func (s *Store) Summary(ctx context.Context) (Summary, error) {
	var result Summary
	err := s.db.QueryRowContext(ctx, candidates+`SELECT revision,
 (SELECT count(*) FROM towers),(SELECT count(*) FROM candidates) FROM catalog_meta WHERE id=1`).Scan(
		&result.Revision, &result.Towers, &result.Flights)
	return result, err
}

func (s *Store) Page(ctx context.Context, kind string, anchor int64, backwards bool, revision, filterToken int64) (Page, error) {
	result := Page{FilterToken: filterToken, Towers: []TowerSummary{}, Flights: []FlightSummary{}}
	if anchor < 0 {
		return result, errors.New("invalid page cursor")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	if err = tx.QueryRowContext(ctx, "SELECT revision FROM catalog_meta WHERE id=1").Scan(&result.Revision); err != nil {
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
		if err = tx.QueryRowContext(ctx, filteredCandidates+"SELECT count(*) FROM candidates").Scan(&result.MatchCount); err != nil {
			return result, err
		}
		result.Flights, err = flightPage(ctx, tx, anchor, order)
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
 (SELECT count(DISTINCT n.legacy_fingerprint) FROM tower_nodes t JOIN nodes n ON n.id=t.node_id WHERE t.tower_id=towers.id AND t.eligible=1),
 substr(failure,1,512),usage FROM towers WHERE id `+order, anchor)
	if err != nil {
		return result, err
	}
	defer rows.Close()
	for rows.Next() {
		var t TowerSummary
		var usage string
		if err = rows.Scan(&t.ID, &t.Name, &t.NodeCount, &t.Failure, &usage); err != nil {
			return result, err
		}
		t.Usage = json.RawMessage(usage)
		result = append(result, t)
	}
	return result, rows.Err()
}

func flightPage(ctx context.Context, tx *sql.Tx, anchor int64, order string) ([]FlightSummary, error) {
	result := []FlightSummary{}
	// 候选先按完整旧指纹全局分组，再按 ID 游标截取页面。
	rows, err := tx.QueryContext(ctx, filteredCandidates+`SELECT n.id AS id,substr(n.name,1,256),substr(n.type,1,32),substr(n.server,1,256),n.port
 FROM candidates c JOIN nodes n ON n.id=c.id WHERE n.id `+order, anchor)
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
	var config string
	err := s.db.QueryRowContext(ctx, "SELECT config FROM nodes WHERE id=?", id).Scan(&config)
	return json.RawMessage(config), err
}
