// 多名称例外随快照变化；恢复普通节点时保留首个完整配置及全部来源。
package catalog

import (
	"context"
	"database/sql"
)

func collapseOrdinaryNodes(ctx context.Context, tx *sql.Tx) error {
	// 先迁移外键引用，再删除重复行，最后清空名称键以避免唯一约束冲突。
	_, err := tx.ExecContext(ctx, `WITH ordinary AS (
 SELECT content_hash,min(id) AS representative FROM nodes
 WHERE NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)
 GROUP BY content_hash
 ) UPDATE tower_nodes SET node_id=(SELECT o.representative FROM ordinary o JOIN nodes n
 ON n.content_hash=o.content_hash WHERE n.id=tower_nodes.node_id)
 WHERE node_id IN (SELECT n.id FROM nodes n JOIN ordinary o ON o.content_hash=n.content_hash
 WHERE n.id<>o.representative)`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `DELETE FROM nodes WHERE
 NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)
 AND id<>(SELECT min(n.id) FROM nodes n WHERE n.content_hash=nodes.content_hash)`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE nodes SET name_key='' WHERE name_key<>''
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)`)
	return err
}
