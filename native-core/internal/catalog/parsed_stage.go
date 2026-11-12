// 过滤仍使用平台既有正则，只回传解析记录位置、来源信息和资格。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
)

type ParsedReference struct {
	Session  int64           `json:"session"`
	Ordinal  int             `json:"ordinal"`
	Eligible bool            `json:"eligible"`
	Source   json.RawMessage `json:"source"`
}

// StageParsed 在同一事务内从解析会话复制，避免完整节点再次跨语言编码。
func (s *Store) StageParsed(ctx context.Context, id, generation int64, start int, refs []ParsedReference) error {
	if start < 0 || len(refs) == 0 {
		return errors.New("invalid parsed references")
	}
	encoded, err := json.Marshal(refs)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, `INSERT INTO refresh_nodes(tower_id,generation,ordinal,payload)
 SELECT ?,?,?+CAST(j.key AS INTEGER),json_set(p.payload,'$.source',json_extract(j.value,'$.source'),
 '$.eligible',json(CASE WHEN json_extract(j.value,'$.eligible') THEN 'true' ELSE 'false' END))
 FROM json_each(?) j JOIN parse_records p ON p.session_id=json_extract(j.value,'$.session')
 AND p.ordinal=json_extract(j.value,'$.ordinal') AND p.kind='node'`, id, generation, start, string(encoded))
	if err != nil {
		return err
	}
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count != int64(len(refs)) {
		return errors.New("parsed session expired or incomplete")
	}
	// 此标记仅由原生复制路径设置；普通stage不能伪造可信ContentHash。
	if _, err = tx.ExecContext(ctx, `INSERT INTO refresh_origin(tower_id,generation,ordinal)
 SELECT ?,?,?+CAST(key AS INTEGER) FROM json_each(?)`, id, generation, start, string(encoded)); err != nil {
		return err
	}
	return tx.Commit()
}

const refreshOptimizationSchema = `
CREATE TABLE IF NOT EXISTS refresh_origin (
 tower_id INTEGER NOT NULL, generation INTEGER NOT NULL, ordinal INTEGER NOT NULL,
 PRIMARY KEY(tower_id,generation,ordinal),
 FOREIGN KEY(tower_id,generation,ordinal) REFERENCES refresh_nodes(tower_id,generation,ordinal) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS refresh_prepared (
 tower_id INTEGER NOT NULL, generation INTEGER NOT NULL, ordinal INTEGER NOT NULL,
 config TEXT NOT NULL, output_key TEXT NOT NULL, source TEXT NOT NULL,
 PRIMARY KEY(tower_id,generation,ordinal),
 FOREIGN KEY(tower_id,generation,ordinal) REFERENCES refresh_nodes(tower_id,generation,ordinal) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS refresh_affected(content_hash TEXT PRIMARY KEY);
CREATE INDEX IF NOT EXISTS tower_info_hashes_content ON tower_info_hashes(content_hash,tower_id);
CREATE INDEX IF NOT EXISTS refresh_nodes_content ON refresh_nodes(tower_id,content_hash);
`
