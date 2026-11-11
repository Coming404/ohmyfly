package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func prepareRefresh(ctx context.Context, tx *sql.Tx, r Refresh, rule flight.DeduplicationConfig) error {
	for ordinal := 0; ordinal < r.Count; ordinal++ {
		var payload string
		if err := tx.QueryRowContext(ctx, "SELECT payload FROM refresh_nodes WHERE tower_id=? AND generation=? AND ordinal=?", r.ID, r.Generation, ordinal).Scan(&payload); err != nil {
			return err
		}
		var node Node
		if err := json.Unmarshal([]byte(payload), &node); err != nil {
			return err
		}
		value, err := nodeFlight(node)
		if err != nil {
			return err
		}
		hash := flight.ContentHash(value)
		if hash == "" {
			return errors.New("invalid node content hash")
		}
		_, err = tx.ExecContext(ctx, `UPDATE refresh_nodes SET content_hash=?,name=?,advanced_key=?,eligible=?
 WHERE tower_id=? AND generation=? AND ordinal=?`, hash, strings.TrimSpace(node.Name), flight.DeduplicationKey(value, rule), node.Eligible, r.ID, r.Generation, ordinal)
		if err != nil {
			return err
		}
	}
	return classifyRefresh(ctx, tx, r.ID)
}

func classifyRefresh(ctx context.Context, tx *sql.Tx, towerID int64) error {
	// 只依据当前数据库快照和本次刷新分类，不能永久记住已经消失的多名称。
	_, err := tx.ExecContext(ctx, `DELETE FROM tower_info_hashes;
 INSERT INTO tower_info_hashes(tower_id,content_hash)
 SELECT t.tower_id,n.content_hash FROM tower_nodes t JOIN nodes n ON n.id=t.node_id
 WHERE t.eligible=1 GROUP BY t.tower_id,n.content_hash HAVING count(DISTINCT trim(n.name))>1`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `INSERT OR IGNORE INTO tower_info_hashes(tower_id,content_hash)
 SELECT ?,content_hash FROM (
 SELECT content_hash,name,row_number() OVER(PARTITION BY
 CASE WHEN advanced_key='' THEN json_array(ordinal) ELSE advanced_key END ORDER BY ordinal) AS rank
 FROM refresh_nodes WHERE tower_id=? AND eligible=1
 ) WHERE rank=1 GROUP BY content_hash HAVING count(DISTINCT name)>1`, towerID, towerID)
	if err != nil {
		return err
	}
	if err = collapseOrdinaryNodes(ctx, tx); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE nodes SET name_key=trim(name) WHERE name_key='' AND EXISTS(
 SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE refresh_nodes SET name_key=CASE WHEN EXISTS(
 SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=refresh_nodes.content_hash)
 THEN name ELSE '' END WHERE tower_id=?`, towerID)
	if err != nil {
		return err
	}
	return groupRefresh(ctx, tx, towerID)
}

func groupRefresh(ctx context.Context, tx *sql.Tx, towerID int64) error {
	// 先完成本批高级与内容去重，再让每个代表进入全库匹配；重复仅写来源关联。
	_, err := tx.ExecContext(ctx, `WITH advanced AS (
 SELECT ordinal,content_hash,name_key,first_value(ordinal) OVER(PARTITION BY
 CASE WHEN advanced_key='' THEN json_array(ordinal) ELSE advanced_key END ORDER BY ordinal) AS first_ordinal
 FROM refresh_nodes WHERE tower_id=? AND eligible=1
 ), basic AS (
 SELECT ordinal,first_value(ordinal) OVER(PARTITION BY content_hash,name_key ORDER BY ordinal) AS first_ordinal
 FROM advanced WHERE ordinal=advanced.first_ordinal
 ) UPDATE refresh_nodes SET representative=(SELECT b.first_ordinal FROM advanced a JOIN basic b ON b.ordinal=a.first_ordinal
 WHERE a.ordinal=refresh_nodes.ordinal) WHERE tower_id=? AND eligible=1`, towerID, towerID)
	return err
}

func commitRefreshNodes(ctx context.Context, tx *sql.Tx, r Refresh, output flight.DeduplicationConfig) error {
	for ordinal := 0; ordinal < r.Count; ordinal++ {
		var payload, hash, nameKey string
		var representative int
		err := tx.QueryRowContext(ctx, `SELECT payload,content_hash,name_key,representative FROM refresh_nodes
 WHERE tower_id=? AND ordinal=?`, r.ID, ordinal).Scan(&payload, &hash, &nameKey, &representative)
		if err != nil {
			return err
		}
		if representative < 0 {
			continue
		}
		var node Node
		if err = json.Unmarshal([]byte(payload), &node); err != nil {
			return err
		}
		if err = insertNode(ctx, tx, r.ID, ordinal, representative, node, hash, nameKey, output); err != nil {
			return err
		}
	}
	return nil
}
