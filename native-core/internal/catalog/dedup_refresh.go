package catalog

import (
	"context"
	"database/sql"
)

func classifyRefresh(ctx context.Context, tx *sql.Tx, towerID int64) error {
	// 只依据当前数据库快照和本次刷新分类，不能永久记住已经消失的多名称。
	_, err := tx.ExecContext(ctx, `DELETE FROM refresh_affected;
 INSERT INTO refresh_affected SELECT DISTINCT content_hash FROM nodes n JOIN tower_nodes t ON t.node_id=n.id WHERE t.tower_id=?;
 INSERT OR IGNORE INTO refresh_affected SELECT DISTINCT content_hash FROM refresh_nodes WHERE tower_id=?`, towerID, towerID)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `DELETE FROM tower_info_hashes WHERE content_hash IN (SELECT content_hash FROM refresh_affected);
 INSERT INTO tower_info_hashes(tower_id,content_hash)
 SELECT t.tower_id,n.content_hash FROM tower_nodes t JOIN nodes n ON n.id=t.node_id
 WHERE t.eligible=1 AND n.content_hash IN (SELECT content_hash FROM refresh_affected)
 GROUP BY t.tower_id,n.content_hash HAVING count(DISTINCT trim(n.name))>1`)
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
	_, err = tx.ExecContext(ctx, `UPDATE nodes SET name_key=trim(name) WHERE name_key='' AND content_hash IN (SELECT content_hash FROM refresh_affected) AND EXISTS(
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

// 代表节点与来源关联通过集合SQL写入，不逐节点返回ID或再次解码JSON。
func commitRefreshNodes(ctx context.Context, tx *sql.Tx, r Refresh) error {
	_, err := tx.ExecContext(ctx, `INSERT INTO nodes(content_hash,name_key,output_key,config,name,type,server,port)
 SELECT n.content_hash,n.name_key,p.output_key,p.config,n.name,json_extract(p.config,'$.type'),
 json_extract(p.config,'$.server'),json_extract(p.config,'$.port')
 FROM refresh_nodes n JOIN refresh_prepared p USING(tower_id,generation,ordinal)
 WHERE n.tower_id=? AND n.generation=? AND n.eligible=1 AND n.representative=n.ordinal
 ORDER BY n.ordinal
 ON CONFLICT(content_hash,name_key) DO UPDATE SET
 config=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.config ELSE nodes.config END,
 name=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.name ELSE nodes.name END,
 output_key=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.output_key ELSE nodes.output_key END`, r.ID, r.Generation)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO tower_nodes(tower_id,ordinal,node_id,source,eligible)
 SELECT n.tower_id,n.ordinal,f.id,p.source,1 FROM refresh_nodes n
 JOIN refresh_prepared p USING(tower_id,generation,ordinal)
 JOIN refresh_nodes representative ON representative.tower_id=n.tower_id AND representative.generation=n.generation AND representative.ordinal=n.representative
 JOIN nodes f ON f.content_hash=representative.content_hash AND f.name_key=representative.name_key
 WHERE n.tower_id=? AND n.generation=? AND n.eligible=1 AND n.representative>=0`, r.ID, r.Generation)
	return err
}
