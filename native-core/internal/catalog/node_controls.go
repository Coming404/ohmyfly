// 节点启停属于应用元数据；批量变更在同一事务中提交。
package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
)

const nodeControlSchema = `CREATE TABLE IF NOT EXISTS node_controls (
 node_id INTEGER PRIMARY KEY REFERENCES nodes(id) ON DELETE CASCADE,
 enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1))
);`

// ChangeNodes 对显式选中的节点执行启停或删除，任一节点失效则整批拒绝。
func (s *Store) ChangeNodes(ctx context.Context, ids []int64, action string) error {
	if len(ids) == 0 || len(ids) > 100000 {
		return errors.New("invalid node selection")
	}
	if action != "enable" && action != "disable" && action != "delete" {
		return errors.New("invalid node action")
	}
	raw, err := json.Marshal(ids)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var count int
	if err = tx.QueryRowContext(ctx, "SELECT count(*) FROM nodes WHERE id IN (SELECT value FROM json_each(?))", string(raw)).Scan(&count); err != nil {
		return err
	}
	if count != len(ids) {
		return ErrStale
	}
	if action == "delete" {
		// 来源关联没有节点删除级联，先在同一事务解除所有来源，再删除节点及级联元数据。
		if _, err = tx.ExecContext(ctx, "DELETE FROM tower_nodes WHERE node_id IN (SELECT value FROM json_each(?))", string(raw)); err != nil {
			return fmt.Errorf("remove selected node sources: %w", err)
		}
		_, err = tx.ExecContext(ctx, "DELETE FROM nodes WHERE id IN (SELECT value FROM json_each(?))", string(raw))
	} else {
		_, err = tx.ExecContext(ctx, `INSERT INTO node_controls(node_id,enabled)
 SELECT value,? FROM json_each(?) WHERE true
 ON CONFLICT(node_id) DO UPDATE SET enabled=excluded.enabled`, action == "enable", string(raw))
	}
	if err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

// SelectionPage 只返回有界 ID 批次；全选不加载节点配置或测量历史。
func (s *Store) SelectionPage(ctx context.Context, kind, search string, towerID, anchor, revision, token int64) ([]int64, error) {
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	var current int64
	if err = tx.QueryRowContext(ctx, "SELECT revision FROM catalog_meta WHERE id=1").Scan(&current); err != nil {
		return nil, err
	}
	if current != revision {
		return nil, ErrStale
	}
	query := ""
	args := []any{anchor}
	switch kind {
	case "towers":
		query = `SELECT id FROM towers WHERE id>? AND id NOT IN (SELECT source_id FROM tower_sources)
 AND instr(lower(name),lower(?))>0 ORDER BY id LIMIT 1000`
		args = append(args, search)
	case "flights":
		if towerID > 0 {
			query = `SELECT id FROM nodes WHERE id>? AND id IN (SELECT node_id FROM tower_nodes
 WHERE eligible=1 AND tower_id IN (SELECT source_id FROM tower_sources WHERE parent_id=? UNION ALL SELECT ?)) ORDER BY id LIMIT 1000`
			args = append(args, towerID, towerID)
		} else {
			if err = checkFilter(ctx, tx, FilterTicket{Token: token, Revision: revision}, true); err != nil {
				return nil, err
			}
			query, err = candidateQuery(ctx, tx, true)
			if err != nil {
				return nil, err
			}
			query += "SELECT id FROM matched WHERE id>? ORDER BY id LIMIT 1000"
		}
	default:
		return nil, errors.New("invalid selection kind")
	}
	rows, err := tx.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	ids := []int64{}
	for rows.Next() {
		var id int64
		if err = rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	if err = rows.Err(); err != nil {
		return nil, err
	}
	if err = rows.Close(); err != nil {
		return nil, err
	}
	return ids, tx.Commit()
}
