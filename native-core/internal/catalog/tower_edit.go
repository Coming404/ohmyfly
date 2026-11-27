package catalog

import (
	"context"
	"database/sql"
	"errors"
	"strings"
)

// ResolveTowerName 在下载得到名称后立即填充，保留用户已经编辑的名称。
func (s *Store) ResolveTowerName(ctx context.Context, id, generation int64, name string) error {
	name = strings.TrimSpace(name)
	if name == "" || len(name) > 256 {
		return errors.New("invalid tower name")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, "UPDATE towers SET name=? WHERE id=? AND name=''", name, id)
	if err != nil {
		return err
	}
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count > 0 {
		if err = bump(ctx, tx); err != nil {
			return err
		}
	}
	return tx.Commit()
}

// UpdateTower 只更新用户编辑的名称和网络地址，不触碰已导入节点。
func (s *Store) UpdateTower(ctx context.Context, id int64, address, name string) error {
	name = strings.TrimSpace(name)
	address = strings.TrimSpace(address)
	if id <= 0 || name == "" || len(name) > 256 {
		return errors.New("invalid tower metadata")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var current string
	if err = tx.QueryRowContext(ctx, "SELECT address FROM towers WHERE id=?", id).Scan(&current); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrStale
		}
		return err
	}
	if strings.HasPrefix(current, "local:") {
		address = current
	} else if address == "" {
		return errors.New("tower address is required")
	}
	if _, err = tx.ExecContext(ctx, "UPDATE towers SET name=?,address=? WHERE id=?", name, address, id); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}
