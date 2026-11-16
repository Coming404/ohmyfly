package catalog

import (
	"context"
	"database/sql"
)

type TowerImportResult struct {
	Added   int64    `json:"added"`
	Updated int64    `json:"updated"`
	Removed int64    `json:"removed"`
	Failed  int64    `json:"failed"`
	Details []string `json:"details"`
}

const towerImportSchema = `CREATE TABLE IF NOT EXISTS tower_imports (
 tower_id INTEGER PRIMARY KEY REFERENCES towers(id) ON DELETE CASCADE,
 added INTEGER NOT NULL DEFAULT 0,
 updated INTEGER NOT NULL DEFAULT 0,
 removed INTEGER NOT NULL DEFAULT 0,
 failed INTEGER NOT NULL DEFAULT 0,
 details TEXT NOT NULL DEFAULT '[]'
);`

// importResult compares eligible content keys before replacing the source's node links.
func importResult(ctx context.Context, tx *sql.Tx, r Refresh) (int64, int64, int64, error) {
	var added, updated, removed int64
	err := tx.QueryRowContext(ctx, `WITH incoming AS (
 SELECT DISTINCT n.content_hash,n.name_key,p.config
 FROM refresh_nodes n JOIN refresh_prepared p USING(tower_id,generation,ordinal)
 WHERE n.tower_id=? AND n.generation=? AND n.eligible=1 AND n.representative=n.ordinal
), previous AS (
 SELECT DISTINCT n.id,n.content_hash,n.name_key,n.config
 FROM tower_nodes t JOIN nodes n ON n.id=t.node_id WHERE t.tower_id=?
), matched AS (
 SELECT p.id,p.config,i.config AS next_config FROM previous p JOIN incoming i USING(content_hash,name_key)
)
 SELECT
 (SELECT count(*) FROM incoming i WHERE NOT EXISTS(SELECT 1 FROM previous p WHERE p.content_hash=i.content_hash AND p.name_key=i.name_key)),
 (SELECT count(*) FROM matched WHERE config<>next_config),
 (SELECT count(*) FROM previous p WHERE NOT EXISTS(SELECT 1 FROM incoming i WHERE i.content_hash=p.content_hash AND i.name_key=p.name_key))`,
		r.ID, r.Generation, r.ID).Scan(&added, &updated, &removed)
	return added, updated, removed, err
}

func markImportFailure(ctx context.Context, tx *sql.Tx, id int64, detail string) error {
	_, err := tx.ExecContext(ctx, `INSERT INTO tower_imports(tower_id,failed,details) VALUES(?,1,json_array(?))
 ON CONFLICT(tower_id) DO UPDATE SET failed=failed+1,details=json_insert(details,'$[#]',?)`, id, detail, detail)
	return err
}

func saveImportResult(ctx context.Context, tx *sql.Tx, id int64, added, updated, removed, failed int64, details string) error {
	_, err := tx.ExecContext(ctx, `INSERT INTO tower_imports(tower_id,added,updated,removed,failed,details)
 VALUES(?,?,?,?,?,?) ON CONFLICT(tower_id) DO UPDATE SET
 added=excluded.added,updated=excluded.updated,removed=excluded.removed,
 failed=excluded.failed,details=excluded.details`, id, added, updated, removed, failed, details)
	return err
}
