// 大节点按字节位置顺序暂存，完整标记与最终刷新事务隔离半成品。
package catalog

import (
	"context"
	"errors"
)

// StageChunk 不在 Go 堆上累积完整节点；SQLite 保存尚未完成的 JSON 文本。
func (s *Store) StageChunk(ctx context.Context, id, generation int64, ordinal, offset int, payload string, final bool) error {
	if ordinal < 0 || offset < 0 || len(payload) == 0 || len(payload) > 256*1024 {
		return errors.New("invalid catalog chunk")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	if offset == 0 {
		if _, err = tx.ExecContext(ctx, `INSERT INTO refresh_nodes(tower_id,generation,ordinal,payload,complete)
 VALUES(?,?,?,'',0)`, id, generation, ordinal); err != nil {
			return err
		}
	}
	var length, complete int
	if err = tx.QueryRowContext(ctx, `SELECT length(CAST(payload AS BLOB)),complete FROM refresh_nodes
 WHERE tower_id=? AND generation=? AND ordinal=?`, id, generation, ordinal).Scan(&length, &complete); err != nil {
		return err
	}
	if complete != 0 || length != offset {
		return errors.New("catalog chunk out of order")
	}
	if _, err = tx.ExecContext(ctx, `UPDATE refresh_nodes SET payload=payload||?,complete=?
 WHERE tower_id=? AND generation=? AND ordinal=?`, payload, final, id, generation, ordinal); err != nil {
		return err
	}
	return tx.Commit()
}
