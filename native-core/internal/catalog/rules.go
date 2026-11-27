package catalog

import (
	"context"
	"database/sql"
	"errors"
)

// 航班筛选只暂存 ID 与匹配位；不改导入资格或刷新代际。
type FilterTicket struct {
	Token    int64 `json:"token"`
	Revision int64 `json:"revision"`
}

type FilterInput struct {
	ID     int64  `json:"id"`
	Name   string `json:"name"`
	Server string `json:"server"`
	Type   string `json:"type"`
}

type FilterDecision struct {
	ID       int64 `json:"id"`
	Eligible bool  `json:"eligible"`
}

func (s *Store) BeginFilter(ctx context.Context, revision int64, scope FlightScope) (FilterTicket, error) {
	if scope.GroupID < -1 || (scope.Status != "all" && scope.Status != "none" && scope.Status != "succeeded" && scope.Status != "failed") {
		return FilterTicket{}, errors.New("invalid flight scope")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return FilterTicket{}, err
	}
	defer tx.Rollback()
	var ticket FilterTicket
	if err = tx.QueryRowContext(ctx, "SELECT revision FROM catalog_meta WHERE id=1").Scan(&ticket.Revision); err != nil {
		return ticket, err
	}
	if revision != ticket.Revision {
		return ticket, ErrStale
	}
	if err = tx.QueryRowContext(ctx, "UPDATE catalog_meta SET filter_generation=filter_generation+1 WHERE id=1 RETURNING filter_generation").Scan(&ticket.Token); err != nil {
		return ticket, err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM filter_results; DELETE FROM filter_work"); err != nil {
		return ticket, err
	}
	if _, err = tx.ExecContext(ctx, "INSERT INTO filter_work(id,token,revision,ready) VALUES(1,?,?,0)", ticket.Token, ticket.Revision); err != nil {
		return ticket, err
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO filter_scope(id,group_id,status,metrics_revision)
 SELECT 1,?,?,metrics_revision FROM catalog_meta WHERE id=1`, scope.GroupID, scope.Status); err != nil {
		return ticket, err
	}
	return ticket, tx.Commit()
}

func checkFilter(ctx context.Context, tx *sql.Tx, ticket FilterTicket, ready bool) error {
	var valid bool
	err := tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM filter_work w JOIN catalog_meta m ON m.id=w.id
 WHERE w.token=? AND w.revision=? AND m.revision=w.revision AND w.ready=?
 AND EXISTS(SELECT 1 FROM filter_scope s WHERE s.id=w.id))`, ticket.Token, ticket.Revision, ready).Scan(&valid)
	if err != nil {
		return err
	}
	if !valid {
		return ErrStale
	}
	return nil
}

const effectiveNodes = `EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id AND t.eligible=1)`

func (s *Store) FilterInputs(ctx context.Context, ticket FilterTicket, after int64) ([]FilterInput, error) {
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	if err = checkFilter(ctx, tx, ticket, false); err != nil {
		return nil, err
	}
	rows, err := tx.QueryContext(ctx, "SELECT id,name,server,type FROM nodes n WHERE id>? AND "+effectiveNodes+" ORDER BY id LIMIT 100", after)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	inputs := make([]FilterInput, 0, 100)
	bytes := 0
	for rows.Next() {
		var input FilterInput
		if err = rows.Scan(&input.ID, &input.Name, &input.Server, &input.Type); err != nil {
			return nil, err
		}
		size := len(input.Name) + len(input.Server) + len(input.Type)
		// 单个大名称完整求值，不按摘要截断。
		if len(inputs) > 0 && bytes+size > 900000 {
			break
		}
		inputs = append(inputs, input)
		bytes += size
	}
	return inputs, rows.Err()
}

func (s *Store) StageFilter(ctx context.Context, ticket FilterTicket, decisions []FilterDecision) error {
	if len(decisions) > 100 {
		return errors.New("invalid filter batch")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkFilter(ctx, tx, ticket, false); err != nil {
		return err
	}
	for _, decision := range decisions {
		if _, err = tx.ExecContext(ctx, "INSERT INTO filter_results(node_id,eligible) VALUES(?,?)", decision.ID, decision.Eligible); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) CommitFilter(ctx context.Context, ticket FilterTicket) (int64, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	if err = checkFilter(ctx, tx, ticket, false); err != nil {
		return 0, err
	}
	var missing bool
	if err = tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM nodes n WHERE "+effectiveNodes+" AND NOT EXISTS(SELECT 1 FROM filter_results r WHERE r.node_id=n.id))").Scan(&missing); err != nil {
		return 0, err
	}
	if missing {
		return 0, errors.New("incomplete filter results")
	}
	query, err := candidateQuery(ctx, tx, true)
	if err != nil {
		return 0, err
	}
	var count int64
	if err = tx.QueryRowContext(ctx, query+"SELECT count(*) FROM matched").Scan(&count); err != nil {
		return 0, err
	}
	if _, err = tx.ExecContext(ctx, "UPDATE filter_work SET ready=1,match_count=? WHERE id=1", count); err != nil {
		return 0, err
	}
	return count, tx.Commit()
}
