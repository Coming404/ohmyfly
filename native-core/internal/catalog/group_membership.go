// 孤立节点回收前，将用户分组决定迁移至相同内容仍有来源的节点。
package catalog

import (
	"context"
	"database/sql"
)

// transferOrphanGroups沿用历史迁移目标；刷新时仅检查本次受影响的内容。
func transferOrphanGroups(ctx context.Context, tx *sql.Tx, affected bool) error {
	scope := ""
	if affected {
		scope = " AND n.content_hash IN (SELECT content_hash FROM refresh_affected)"
	}
	_, err := tx.ExecContext(ctx, `WITH orphan_members AS (
 SELECT g.group_id,g.changed_revision,n.id,
 (SELECT min(live.id) FROM nodes live WHERE live.content_hash=n.content_hash
 AND EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=live.id)) AS target
 FROM node_groups g JOIN nodes n ON n.id=g.node_id
 WHERE NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id)`+scope+`
 ), ranked AS (
 SELECT *,row_number() OVER(PARTITION BY target ORDER BY changed_revision DESC,id DESC) AS rank
 FROM orphan_members WHERE target IS NOT NULL
 ) INSERT INTO node_groups(node_id,group_id,changed_revision)
 SELECT target,group_id,changed_revision FROM ranked WHERE rank=1
 ON CONFLICT(node_id) DO UPDATE SET group_id=excluded.group_id,changed_revision=excluded.changed_revision
 WHERE excluded.changed_revision>node_groups.changed_revision`)
	return err
}
