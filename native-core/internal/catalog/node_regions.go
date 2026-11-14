// 节点地区是应用元数据，与协议配置和 ContentHash 分离。
package catalog

import (
	"context"
	"database/sql"
)

const nodeRegionSchema = `
CREATE INDEX IF NOT EXISTS tower_nodes_region_page ON tower_nodes(tower_id,node_id);
CREATE TABLE IF NOT EXISTS region_catalog (
 code TEXT PRIMARY KEY,
 name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS node_regions (
 node_id INTEGER PRIMARY KEY REFERENCES nodes(id) ON DELETE CASCADE,
 name_region TEXT NOT NULL DEFAULT '',
 manual_region TEXT NOT NULL DEFAULT ''
);
`

// initRegions 首次建表时分批补齐已有节点；后续启动仅更新中文目录。
func initRegions(ctx context.Context, tx *sql.Tx) error {
	if _, err := tx.ExecContext(ctx, nodeRegionSchema); err != nil {
		return err
	}
	var count int
	if err := tx.QueryRowContext(ctx, "SELECT count(*) FROM region_catalog").Scan(&count); err != nil {
		return err
	}
	for _, row := range regionCatalogRows {
		if _, err := tx.ExecContext(ctx, "INSERT INTO region_catalog(code,name) VALUES(?,?) ON CONFLICT(code) DO UPDATE SET name=excluded.name", row.Code, row.Name); err != nil {
			return err
		}
	}
	if count == 0 {
		return applyNameRegions(ctx, tx, 0)
	}
	return nil
}

// applyNameRegions 有界读取完整名称；名称结果与手动值分别保存，避免覆盖手动值。
func applyNameRegions(ctx context.Context, tx *sql.Tx, towerID int64) error {
	var anchor int64
	for {
		query := "SELECT id,name FROM nodes WHERE id>?"
		args := []any{anchor}
		if towerID != 0 {
			// 在来源索引内先取有界节点集合，避免每批重复展开整座塔台。
			query = `SELECT id,name FROM nodes WHERE id IN (
 SELECT DISTINCT node_id FROM tower_nodes WHERE tower_id=? AND node_id>?
 ORDER BY node_id LIMIT 100)`
			args = []any{towerID, anchor}
		}
		rows, err := tx.QueryContext(ctx, query+" ORDER BY id LIMIT 100", args...)
		if err != nil {
			return err
		}
		type item struct {
			id   int64
			name string
		}
		items := make([]item, 0, 100)
		for rows.Next() {
			var v item
			if err = rows.Scan(&v.id, &v.name); err != nil {
				rows.Close()
				return err
			}
			items = append(items, v)
		}
		err = rows.Err()
		closeErr := rows.Close()
		if err != nil {
			return err
		}
		if closeErr != nil {
			return closeErr
		}
		if len(items) == 0 {
			return nil
		}
		for _, v := range items {
			code := regionFromName(v.name)
			if _, err = tx.ExecContext(ctx, `INSERT INTO node_regions(node_id,name_region) VALUES(?,?)
 ON CONFLICT(node_id) DO UPDATE SET name_region=excluded.name_region
 WHERE node_regions.name_region<>excluded.name_region`, v.id, code); err != nil {
				return err
			}
			anchor = v.id
		}
	}
}
