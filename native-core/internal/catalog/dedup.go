package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

type DedupSettings struct {
	Output flight.DeduplicationConfig `json:"output"`
}

func (s *Store) SetDedup(ctx context.Context, settings DedupSettings) error {
	if err := settings.Output.Validate(false); err != nil {
		return err
	}
	encoded, err := json.Marshal(settings.Output)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var previous string
	if err = tx.QueryRowContext(ctx, "SELECT output_rules FROM catalog_meta WHERE id=1").Scan(&previous); err != nil {
		return err
	}
	if previous == string(encoded) {
		return nil
	}
	if err = updateOutputKeys(ctx, tx, settings.Output); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "UPDATE catalog_meta SET output_rules=? WHERE id=1", string(encoded)); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func updateOutputKeys(ctx context.Context, tx *sql.Tx, rule flight.DeduplicationConfig) error {
	var anchor int64
	for {
		var id int64
		var config string
		err := tx.QueryRowContext(ctx, "SELECT id,config FROM nodes WHERE id>? ORDER BY id LIMIT 1", anchor).Scan(&id, &config)
		if errors.Is(err, sql.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		var node Node
		if err = json.Unmarshal([]byte(config), &node); err != nil {
			return err
		}
		value, err := nodeFlight(node)
		if err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, "UPDATE nodes SET output_key=? WHERE id=?", flight.DeduplicationKey(value, rule), id); err != nil {
			return err
		}
		anchor = id
	}
}

func (s *Store) SetTowerDedup(ctx context.Context, id int64, rule flight.DeduplicationConfig) error {
	if err := rule.Validate(true); err != nil {
		return err
	}
	encoded, err := json.Marshal(rule)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, "UPDATE towers SET dedup_rules=?,generation=generation+1 WHERE id=? OR id IN (SELECT source_id FROM tower_sources WHERE parent_id=?)", string(encoded), id, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=? OR tower_id IN (SELECT source_id FROM tower_sources WHERE parent_id=?)", id, id); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func nodeFlight(node Node) (flight.Flight, error) {
	var fields map[string]any
	decoder := json.NewDecoder(strings.NewReader(string(node.Fields)))
	decoder.UseNumber()
	if err := decoder.Decode(&fields); err != nil {
		return flight.Flight{}, err
	}
	return flight.Flight{Name: node.Name, Type: node.Type, Server: node.Server, Port: node.Port, Fields: fields}, nil
}
