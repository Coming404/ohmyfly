package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"github.com/Coming404/ohmyfly/native-core/internal/flight"
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
	DedupRules flight.DeduplicationConfig `json:"dedupRules"`
	ID         int64                      `json:"id"`
	Generation int64                      `json:"generation"`
	Address    string                     `json:"address"`
	Name       string                     `json:"name"`
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
	var rule string
	err = tx.QueryRowContext(ctx, `UPDATE towers SET generation=generation+1 WHERE id=?
 RETURNING id,generation,address,name,dedup_rules`, id).Scan(&t.ID, &t.Generation, &t.Address, &t.Name, &rule)
	if err != nil {
		return t, err
	}
	if err = json.Unmarshal([]byte(rule), &t.DedupRules); err != nil {
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
	if len(nodes) > 100 || start < 0 {
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
	towerRule, outputRule, err := loadDedupRules(ctx, tx, r.ID)
	if err != nil {
		return err
	}
	if err = prepareRefresh(ctx, tx, r, towerRule); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM tower_nodes WHERE tower_id=?", r.ID); err != nil {
		return err
	}
	if err = commitRefreshNodes(ctx, tx, r, outputRule); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE towers SET name=CASE WHEN name='' THEN ? ELSE name END,
 usage=?,warnings=?,failure='',committed_generation=? WHERE id=?`, r.Name, string(r.Usage), string(r.Warnings), r.Generation, r.ID)
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", r.ID); err != nil {
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

func (s *Store) Fail(ctx context.Context, id, generation int64, failure, name string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
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
