package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"
)

type Node struct {
	Name         string          `json:"name"`
	Type         string          `json:"type"`
	Server       string          `json:"server"`
	Port         int             `json:"port"`
	Fields       json.RawMessage `json:"fields"`
	RawInput     string          `json:"rawInput"`
	SourceFormat string          `json:"sourceFormat"`
	FromBase64   bool            `json:"fromBase64"`
	ContentHash  string          `json:"contentHash"`
	Source       json.RawMessage `json:"source"`
	Eligible     bool            `json:"eligible"`
}

type Ticket struct {
	ID         int64  `json:"id"`
	Generation int64  `json:"generation"`
	Address    string `json:"address"`
	Name       string `json:"name"`
}

type Refresh struct {
	ID         int64           `json:"id"`
	Generation int64           `json:"generation"`
	Name       string          `json:"name"`
	Usage      json.RawMessage `json:"usage"`
	Warnings   json.RawMessage `json:"warnings"`
	Count      int             `json:"count"`
}

var ErrStale = errors.New("catalog revision changed")

func (s *Store) Add(ctx context.Context, address, name string) (int64, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	var id int64
	err = tx.QueryRowContext(ctx, `INSERT INTO towers(address,name) VALUES(?,?)
 ON CONFLICT(address) DO UPDATE SET name=CASE WHEN towers.name='' THEN excluded.name ELSE towers.name END RETURNING id`, address, name).Scan(&id)
	if err != nil {
		return 0, err
	}
	if err = bump(ctx, tx); err != nil {
		return 0, err
	}
	return id, tx.Commit()
}

func (s *Store) Begin(ctx context.Context, id int64) (Ticket, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Ticket{}, err
	}
	defer tx.Rollback()
	var t Ticket
	err = tx.QueryRowContext(ctx, `UPDATE towers SET generation=generation+1,last_attempt_at=? WHERE id=?
 RETURNING id,generation,address,name`, time.Now().UnixMilli(), id).Scan(&t.ID, &t.Generation, &t.Address, &t.Name)
	if errors.Is(err, sql.ErrNoRows) {
		return t, ErrStale
	}
	if err != nil {
		return t, err
	}
	if err = tx.QueryRowContext(ctx, `SELECT coalesce((SELECT address FROM tower_sources WHERE source_id=?),?)`, id, t.Address).Scan(&t.Address); err != nil {
		return t, err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", id); err != nil {
		return t, err
	}
	return t, tx.Commit()
}

func checkTicket(ctx context.Context, tx *sql.Tx, id, generation int64) error {
	var current int64
	if err := tx.QueryRowContext(ctx, "SELECT generation FROM towers WHERE id=?", id).Scan(&current); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrStale
		}
		return err
	}
	if current != generation {
		return ErrStale
	}
	return nil
}

func (s *Store) Stage(ctx context.Context, id, generation int64, start int, nodes []Node) error {
	// 移动桥接已限定请求字节数；暂存批次不再按节点条数截断。
	if start < 0 {
		return errors.New("invalid catalog batch")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	for i, node := range nodes {
		payload, err := json.Marshal(node)
		if err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, "INSERT INTO refresh_nodes(tower_id,generation,ordinal,payload) VALUES(?,?,?,?)", id, generation, start+i, string(payload)); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) Commit(ctx context.Context, r Refresh) error {
	if r.Count < 0 {
		return errors.New("invalid catalog refresh count")
	}
	rules, err := s.prepareRefresh(ctx, r)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, r.ID, r.Generation); err != nil {
		return err
	}
	var count int
	if err = tx.QueryRowContext(ctx, "SELECT count(*) FROM refresh_nodes WHERE tower_id=? AND generation=? AND complete=1", r.ID, r.Generation).Scan(&count); err != nil {
		return err
	}
	if count != r.Count {
		return errors.New("incomplete catalog refresh")
	}
	var towerRule, outputRule string
	if err = tx.QueryRowContext(ctx, "SELECT dedup_rules,output_rules FROM towers,catalog_meta WHERE towers.id=? AND catalog_meta.id=1", r.ID).Scan(&towerRule, &outputRule); err != nil {
		return err
	}
	if towerRule != rules.tower || outputRule != rules.output {
		return ErrStale
	}
	if err = classifyRefresh(ctx, tx, r.ID); err != nil {
		return err
	}
	added, updated, removed, err := importResult(ctx, tx, r)
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM tower_nodes WHERE tower_id=?", r.ID); err != nil {
		return err
	}
	if err = commitRefreshNodes(ctx, tx, r); err != nil {
		return err
	}
	details := string(r.Warnings)
	if details == "" || details == "null" {
		details = "[]"
	}
	if err = saveImportResult(ctx, tx, r.ID, added, updated, removed, 0, details); err != nil {
		return err
	}
	if err = applyNameRegions(ctx, tx, r.ID); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE towers SET name=CASE WHEN name='' THEN ? ELSE name END,
 usage=?,warnings=?,failure='',committed_generation=?,last_success_at=? WHERE id=?`, r.Name, string(r.Usage), string(r.Warnings), r.Generation, time.Now().UnixMilli(), r.ID)
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", r.ID); err != nil {
		return err
	}
	if err = collectRefresh(ctx, tx); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) Fail(ctx context.Context, id, generation int64, failure, name string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	if err = markImportFailure(ctx, tx, id, failure); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "UPDATE towers SET failure=?,name=CASE WHEN name='' THEN ? ELSE name END WHERE id=?", failure, name, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", id); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) Remove(ctx context.Context, id int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	// 先删独占的子来源，外键清理关联后再回收零引用节点。
	if _, err = tx.ExecContext(ctx, "DELETE FROM towers WHERE id IN (SELECT source_id FROM tower_sources WHERE parent_id=?)", id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM towers WHERE id=?", id); err != nil {
		return err
	}
	if err = collect(ctx, tx); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}
