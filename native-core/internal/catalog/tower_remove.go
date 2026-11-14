// 批量塔台删除沿用单塔台来源级联与孤立节点回收语义。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
)

// RemoveTowers 在单个事务中删除所选父塔台和独占子来源。
func (s *Store) RemoveTowers(ctx context.Context, ids []int64) error {
	if len(ids) == 0 {
		return errors.New("empty tower selection")
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
	if _, err = tx.ExecContext(ctx, `DELETE FROM towers WHERE id IN (
 SELECT source_id FROM tower_sources WHERE parent_id IN (SELECT value FROM json_each(?)))`, string(raw)); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM towers WHERE id IN (SELECT value FROM json_each(?))", string(raw)); err != nil {
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
