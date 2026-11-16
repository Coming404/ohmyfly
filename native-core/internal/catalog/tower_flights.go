package catalog

import (
	"context"
	"database/sql"
	"errors"
)

// TowerFlights 按需读取一个来源的已通过导入过滤的节点，不改变全局航班筛选。
func (s *Store) TowerFlights(ctx context.Context, towerID, anchor, revision int64) (Page, error) {
	// 两个集合都输出空数组，保持与共享层非空分页契约一致。
	result := Page{Towers: []TowerSummary{}, Flights: []FlightSummary{}}
	if towerID <= 0 || anchor < 0 {
		return result, errors.New("invalid tower page cursor")
	}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	if err = tx.QueryRowContext(ctx, "SELECT revision,metrics_revision FROM catalog_meta WHERE id=1").Scan(&result.Revision, &result.MetricsRevision); err != nil {
		return result, err
	}
	if revision != result.Revision {
		return result, ErrStale
	}
	if err = tx.QueryRowContext(ctx, "SELECT count(DISTINCT node_id) FROM tower_nodes WHERE tower_id IN (SELECT source_id FROM tower_sources WHERE parent_id=? UNION ALL SELECT ?) AND eligible=1", towerID, towerID).Scan(&result.MatchCount); err != nil {
		return result, err
	}
	rows, err := tx.QueryContext(ctx, `SELECT n.id,substr(n.name,1,256),substr(n.type,1,32),substr(n.server,1,256),n.port
 FROM nodes n WHERE n.id>? AND n.id IN(SELECT t.node_id FROM tower_nodes t
 WHERE t.tower_id IN (SELECT source_id FROM tower_sources WHERE parent_id=? UNION ALL SELECT ?) AND t.eligible=1)
 ORDER BY n.id LIMIT 100`, anchor, towerID, towerID)
	if err != nil {
		return result, err
	}
	for rows.Next() {
		var node FlightSummary
		if err = rows.Scan(&node.ID, &node.Name, &node.Type, &node.Server, &node.Port); err != nil {
			rows.Close()
			return result, err
		}
		result.Flights = append(result.Flights, node)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return result, err
	}
	if err = attachRegions(ctx, tx, result.Flights); err != nil {
		return result, err
	}
	if err = attachMeasurements(ctx, tx, result.Flights); err != nil {
		return result, err
	}
	return result, tx.Commit()
}
