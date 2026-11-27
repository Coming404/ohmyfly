// 解析结果按字节缓冲，持久化时才取得写连接，不在解析回调期间持有事务。
package catalog

import (
	"context"
	"encoding/json"
	"errors"
)

const ParseBatchBytes = 900_000

type ParseWriter struct {
	store   *Store
	session int64
	kind    string
	start   int
	bytes   int
	records []json.RawMessage
}

func (s *Store) NewParseWriter(session int64, kind string) *ParseWriter {
	return &ParseWriter{store: s, session: session, kind: kind}
}

func (w *ParseWriter) Append(ctx context.Context, value any) error {
	payload, err := json.Marshal(value)
	if err != nil {
		return err
	}
	if w.bytes+len(payload) > ParseBatchBytes {
		if err = w.Flush(ctx); err != nil {
			return err
		}
	}
	w.records = append(w.records, payload)
	w.bytes += len(payload)
	if w.bytes >= ParseBatchBytes {
		return w.Flush(ctx)
	}
	return nil
}

func (w *ParseWriter) Flush(ctx context.Context) error {
	if len(w.records) == 0 {
		return nil
	}
	// 编码在写事务之外完成；SQL集合写入避免逐节点调用驱动。
	encoded, err := json.Marshal(w.records)
	if err != nil {
		return err
	}
	if err := w.store.WaitForInteraction(ctx); err != nil {
		return err
	}
	tx, err := w.store.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	_, err = tx.ExecContext(ctx, `INSERT INTO parse_records(session_id,kind,ordinal,payload)
 SELECT ?,?,?+CAST(key AS INTEGER),value FROM json_each(?)`, w.session, w.kind, w.start, string(encoded))
	if err != nil {
		return err
	}
	if err = tx.Commit(); err != nil {
		return err
	}
	w.start += len(w.records)
	w.records = nil
	w.bytes = 0
	return nil
}

type ParsedPage struct {
	Records []json.RawMessage `json:"records"`
}

// 单个超大记录单独返回；正常记录限制总字节而非每节点一次跨语言调用。
func (s *Store) ParsedPage(ctx context.Context, id int64, kind string, start int) (ParsedPage, error) {
	result := ParsedPage{Records: []json.RawMessage{}}
	if start < 0 || (kind != "node" && kind != "provider") {
		return result, errors.New("invalid parsed cursor")
	}
	rows, err := s.readDB.QueryContext(ctx, `SELECT payload FROM parse_records WHERE session_id=? AND kind=? AND ordinal>=? ORDER BY ordinal`, id, kind, start)
	if err != nil {
		return result, err
	}
	defer rows.Close()
	size := 2
	for rows.Next() {
		var payload string
		if err = rows.Scan(&payload); err != nil {
			return result, err
		}
		if len(result.Records) > 0 && size+len(payload)+1 > ParseBatchBytes {
			break
		}
		result.Records = append(result.Records, json.RawMessage(payload))
		size += len(payload) + 1
		if size >= ParseBatchBytes {
			break
		}
	}
	return result, rows.Err()
}
