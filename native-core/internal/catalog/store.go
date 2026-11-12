package catalog

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/url"
	"path/filepath"

	"github.com/Coming404/ohmyfly/native-core/internal/sqlite"
)

type Store struct{ db *sql.DB }

func Open(ctx context.Context, path string) (*Store, error) {
	if !filepath.IsAbs(path) {
		return nil, errors.New("catalog path must be absolute")
	}
	dsn := (&url.URL{Scheme: "file", Path: path}).String() +
		"?_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)&_pragma=synchronous(EXTRA)&_pragma=secure_delete(1)"
	db, err := sqlite.Open(ctx, dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	s := &Store{db: db}
	if err := s.migrate(ctx); err != nil {
		return nil, errors.Join(err, db.Close())
	}
	return s, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) migrate(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx, "PRAGMA journal_mode=DELETE"); err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var version int
	if err := tx.QueryRowContext(ctx, "PRAGMA user_version").Scan(&version); err != nil {
		return err
	}
	if version > 1 {
		return fmt.Errorf("unsupported catalog version %d", version)
	}
	if version == 0 {
		if _, err := tx.ExecContext(ctx, schema); err != nil {
			return err
		}
	}
	var metricsRevision int64
	if err := tx.QueryRowContext(ctx, "SELECT metrics_revision FROM catalog_meta WHERE id=1").Scan(&metricsRevision); err != nil {
		return fmt.Errorf("catalog v1 schema is incomplete; explicit upgrade decision required: %w", err)
	}
	if err := interruptTasks(ctx, tx); err != nil {
		return err
	}
	// 暂存从未成为目录事实；进程重启后丢弃未提交的刷新。
	if _, err := tx.ExecContext(ctx, "DELETE FROM parse_sessions; DELETE FROM refresh_nodes; DELETE FROM filter_results; DELETE FROM filter_work; UPDATE towers SET generation=generation+1"); err != nil {
		return err
	}
	return tx.Commit()
}

const schema = `
CREATE TABLE catalog_meta (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL,
 metrics_revision INTEGER NOT NULL DEFAULT 0,
 filter_generation INTEGER NOT NULL DEFAULT 0,
 output_rules TEXT NOT NULL DEFAULT '{"mode":"none"}');
INSERT INTO catalog_meta(id,revision) VALUES(1,1);
CREATE TABLE towers (
 id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL UNIQUE, name TEXT NOT NULL DEFAULT '',
 generation INTEGER NOT NULL DEFAULT 0, committed_generation INTEGER NOT NULL DEFAULT 0,
 dedup_rules TEXT NOT NULL DEFAULT '{"mode":"none"}',
 usage TEXT NOT NULL DEFAULT 'null', failure TEXT NOT NULL DEFAULT '', warnings TEXT NOT NULL DEFAULT '[]',
 last_attempt_at INTEGER, last_success_at INTEGER
);
CREATE TABLE nodes (
 id INTEGER PRIMARY KEY AUTOINCREMENT, content_hash TEXT NOT NULL,
 name_key TEXT NOT NULL DEFAULT '', output_key TEXT NOT NULL DEFAULT '', config TEXT NOT NULL,
 name TEXT NOT NULL, type TEXT NOT NULL, server TEXT NOT NULL, port INTEGER NOT NULL,
 UNIQUE(content_hash,name_key)
);
CREATE INDEX nodes_content_hash ON nodes(content_hash,id);
CREATE TABLE tower_nodes (
 tower_id INTEGER NOT NULL REFERENCES towers(id) ON DELETE CASCADE,
 ordinal INTEGER NOT NULL, node_id INTEGER NOT NULL REFERENCES nodes(id),
 source TEXT NOT NULL, eligible INTEGER NOT NULL, PRIMARY KEY(tower_id,ordinal)
);
CREATE TABLE tower_info_hashes (
 tower_id INTEGER NOT NULL REFERENCES towers(id) ON DELETE CASCADE,
 content_hash TEXT NOT NULL, PRIMARY KEY(tower_id,content_hash)
);
CREATE INDEX tower_nodes_node ON tower_nodes(node_id,eligible);
CREATE INDEX tower_nodes_eligible ON tower_nodes(eligible,node_id);
CREATE TABLE refresh_nodes (
 tower_id INTEGER NOT NULL REFERENCES towers(id) ON DELETE CASCADE, generation INTEGER NOT NULL,
 ordinal INTEGER NOT NULL, payload TEXT NOT NULL, complete INTEGER NOT NULL DEFAULT 1,
 content_hash TEXT NOT NULL DEFAULT '', name TEXT NOT NULL DEFAULT '', advanced_key TEXT NOT NULL DEFAULT '',
 eligible INTEGER NOT NULL DEFAULT 1, name_key TEXT NOT NULL DEFAULT '', representative INTEGER NOT NULL DEFAULT -1,
 final_node INTEGER,
 PRIMARY KEY(tower_id,generation,ordinal)
);
CREATE TABLE parse_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT);
CREATE TABLE parse_chunks (session_id INTEGER NOT NULL REFERENCES parse_sessions(id) ON DELETE CASCADE,
 ordinal INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(session_id,ordinal));
CREATE TABLE parse_records (session_id INTEGER NOT NULL REFERENCES parse_sessions(id) ON DELETE CASCADE,
 kind TEXT NOT NULL, ordinal INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(session_id,kind,ordinal));
CREATE TABLE filter_work (id INTEGER PRIMARY KEY CHECK(id=1), token INTEGER NOT NULL,
 revision INTEGER NOT NULL, ready INTEGER NOT NULL, match_count INTEGER NOT NULL DEFAULT 0);
CREATE TABLE filter_results (node_id INTEGER PRIMARY KEY REFERENCES nodes(id) ON DELETE CASCADE,
 eligible INTEGER NOT NULL);
` + resultSchema + `
PRAGMA user_version=1;`

func bump(ctx context.Context, tx *sql.Tx) error {
	_, err := tx.ExecContext(ctx, "UPDATE catalog_meta SET revision=revision+1 WHERE id=1")
	return err
}

func collect(ctx context.Context, tx *sql.Tx) error {
	// 同配置还有来源时保留历史；不同 ContentHash 或最后来源消失时由外键级联回收。
	if _, err := tx.ExecContext(ctx, `UPDATE node_runs SET node_id=(
 SELECT min(n.id) FROM nodes n WHERE n.content_hash=(SELECT content_hash FROM nodes WHERE id=node_runs.node_id)
 AND EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id))
 WHERE NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=node_runs.node_id)
 AND EXISTS(SELECT 1 FROM nodes n JOIN tower_nodes t ON t.node_id=n.id
 WHERE n.content_hash=(SELECT content_hash FROM nodes WHERE id=node_runs.node_id))`); err != nil {
		return err
	}
	_, err := tx.ExecContext(ctx, "DELETE FROM nodes WHERE NOT EXISTS(SELECT 1 FROM tower_nodes WHERE node_id=nodes.id)")
	return err
}
