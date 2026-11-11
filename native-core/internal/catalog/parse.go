package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"strings"
)

func (s *Store) BeginParse(ctx context.Context) (int64, error) {
	var id int64
	err := s.db.QueryRowContext(ctx, "INSERT INTO parse_sessions DEFAULT VALUES RETURNING id").Scan(&id)
	return id, err
}

func (s *Store) ParseChunk(ctx context.Context, id int64, ordinal int, payload string) error {
	_, err := s.db.ExecContext(ctx, "INSERT INTO parse_chunks(session_id,ordinal,payload) VALUES(?,?,?)", id, ordinal, payload)
	return err
}

type parseReader struct {
	store   *Store
	ctx     context.Context
	id      int64
	ordinal int
	current *strings.Reader
}

func (s *Store) ParseReader(ctx context.Context, id int64) io.Reader {
	return &parseReader{store: s, ctx: ctx, id: id}
}

func (r *parseReader) Read(p []byte) (int, error) {
	for {
		if r.current != nil {
			n, err := r.current.Read(p)
			if n > 0 {
				return n, nil
			}
			if err != io.EOF {
				return n, err
			}
		}
		var text string
		err := r.store.db.QueryRowContext(r.ctx, "SELECT payload FROM parse_chunks WHERE session_id=? AND ordinal=?", r.id, r.ordinal).Scan(&text)
		if errors.Is(err, sql.ErrNoRows) {
			return 0, io.EOF
		}
		if err != nil {
			return 0, err
		}
		r.ordinal++
		r.current = strings.NewReader(text)
	}
}

func (s *Store) SaveParsed(ctx context.Context, id int64, kind string, ordinal int, value any) error {
	payload, err := json.Marshal(value)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, "INSERT INTO parse_records(session_id,kind,ordinal,payload) VALUES(?,?,?,?)", id, kind, ordinal, string(payload))
	return err
}

func (s *Store) Parsed(ctx context.Context, id int64, kind string, ordinal int) (json.RawMessage, error) {
	var payload string
	err := s.db.QueryRowContext(ctx, "SELECT payload FROM parse_records WHERE session_id=? AND kind=? AND ordinal=?", id, kind, ordinal).Scan(&payload)
	return json.RawMessage(payload), err
}

func (s *Store) ClearParseInput(ctx context.Context, id int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM parse_chunks WHERE session_id=?", id)
	return err
}

func (s *Store) QueuePush(ctx context.Context, id int64, ordinal int) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, `INSERT INTO parse_records(session_id,kind,ordinal,payload)
 SELECT ?,'queue',?,group_concat(payload,'') FROM (SELECT payload FROM parse_chunks WHERE session_id=? ORDER BY ordinal)`, id, ordinal, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM parse_chunks WHERE session_id=?", id); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) QueuePop(ctx context.Context, id int64, ordinal int) (json.RawMessage, error) {
	var payload string
	err := s.db.QueryRowContext(ctx, "DELETE FROM parse_records WHERE session_id=? AND kind='queue' AND ordinal=? RETURNING payload", id, ordinal).Scan(&payload)
	return json.RawMessage(payload), err
}

func (s *Store) EndParse(ctx context.Context, id int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM parse_sessions WHERE id=?", id)
	return err
}
