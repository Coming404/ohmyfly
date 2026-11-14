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
 WHERE content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)
 GROUP BY content_hash
 ) UPDATE tower_nodes SET node_id=(SELECT o.representative FROM ordinary o JOIN nodes n
 ON n.content_hash=o.content_hash WHERE n.id=tower_nodes.node_id)
 WHERE node_id IN (SELECT n.id FROM nodes n JOIN ordinary o ON o.content_hash=n.content_hash
 WHERE n.id<>o.representative)`)
	if err != nil {
		return err
	}
	// 只合并相同 ContentHash 的历史；run ID 保留，因此同任务原来的两个节点仍各有真实记录。
	if _, err := tx.ExecContext(ctx, `UPDATE node_runs SET node_id=(SELECT min(n.id) FROM nodes n
 WHERE n.content_hash=(SELECT content_hash FROM nodes WHERE id=node_runs.node_id))
 WHERE node_id IN (SELECT id FROM nodes WHERE content_hash IN (SELECT content_hash FROM refresh_affected))
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i JOIN nodes n ON n.content_hash=i.content_hash
 WHERE n.id=node_runs.node_id)`); err != nil {
		return err
	}
	// 多名称收敛时使用最近一次显式移动的归属，避免删除别名时丢失用户分组。
	if _, err := tx.ExecContext(ctx, `WITH memberships AS (
 SELECT min(n.id) OVER(PARTITION BY n.content_hash) AS representative,
 g.group_id,g.changed_revision,
 row_number() OVER(PARTITION BY n.content_hash ORDER BY g.changed_revision DESC,g.node_id DESC) AS rank
 FROM nodes n LEFT JOIN node_groups g ON g.node_id=n.id
 WHERE n.content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=n.content_hash)
 ) INSERT INTO node_groups(node_id,group_id,changed_revision)
 SELECT representative,group_id,changed_revision FROM memberships WHERE rank=1 AND changed_revision IS NOT NULL
 ON CONFLICT(node_id) DO UPDATE SET group_id=excluded.group_id,changed_revision=excluded.changed_revision`); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `DELETE FROM nodes WHERE content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)
 AND id<>(SELECT min(n.id) FROM nodes n WHERE n.content_hash=nodes.content_hash)`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE nodes SET name_key='' WHERE name_key<>''
 AND content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_info_hashes i WHERE i.content_hash=nodes.content_hash)`)
	return err
}
