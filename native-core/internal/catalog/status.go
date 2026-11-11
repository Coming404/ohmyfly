package catalog

import (
	"context"
	"database/sql"
	"errors"
)

type RefreshStatus struct {
	Exists     bool  `json:"exists"`
	Generation int64 `json:"generation"`
	Committed  int64 `json:"committed"`
}

func (s *Store) RefreshStatus(ctx context.Context, id int64) (RefreshStatus, error) {
	var status RefreshStatus
	err := s.db.QueryRowContext(ctx, "SELECT generation,committed_generation FROM towers WHERE id=?", id).Scan(&status.Generation, &status.Committed)
	if errors.Is(err, sql.ErrNoRows) {
		return status, nil
	}
	status.Exists = err == nil
	return status, err
}

func (s *Store) Abort(ctx context.Context, id, generation int64) error {
	_, err := s.db.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=? AND generation=?", id, generation)
	return err
}
