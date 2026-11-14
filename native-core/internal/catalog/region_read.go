// 地区摘要只读取当前页节点，不改变原有游标查询计划。
package catalog

import (
	"context"
	"database/sql"
	"strings"
)

// attachRegions 按名称、手动、未知顺序解析显示名；cloudflare 标识专用品牌图标。
func attachRegions(ctx context.Context, tx *sql.Tx, nodes []FlightSummary) error {
	if len(nodes) == 0 {
		return nil
	}
	ids := make([]any, len(nodes))
	positions := make(map[int64]int, len(nodes))
	for i := range nodes {
		ids[i] = nodes[i].ID
		positions[nodes[i].ID] = i
		nodes[i].Region = "未知"
	}
	rows, err := tx.QueryContext(ctx, `SELECT n.id,coalesce(c.code,''),coalesce(c.name,'未知'),coalesce(o.enabled,1)
 FROM nodes n LEFT JOIN node_regions r ON r.node_id=n.id
 LEFT JOIN region_catalog c ON c.code=coalesce(nullif(r.name_region,''),nullif(r.manual_region,''))
 LEFT JOIN node_controls o ON o.node_id=n.id
 WHERE n.id IN (`+strings.TrimSuffix(strings.Repeat("?,", len(ids)), ",")+")", ids...)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var id int64
		var code, name string
		var enabled bool
		if err = rows.Scan(&id, &code, &name, &enabled); err != nil {
			return err
		}
		node := &nodes[positions[id]]
		node.Enabled = enabled
		node.Region = name
		node.RegionCode = code
	}
	return rows.Err()
}
