// 刷新只可能使本次涉及的内容失去来源，回收时不扫描无关节点及其检测历史。
package catalog

import (
	"context"
	"database/sql"
)

// collectRefresh 将失去来源的历史迁移到同内容存活节点，再回收零引用节点。
func collectRefresh(ctx context.Context, tx *sql.Tx) error {
	if err := transferOrphanGroups(ctx, tx, true); err != nil {
		return err
	}
	_, err := tx.ExecContext(ctx, `WITH orphan AS (
 SELECT n.id,n.content_hash FROM nodes n
 WHERE n.content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id)
 ) UPDATE node_runs SET node_id=(
 SELECT min(n.id) FROM nodes n WHERE n.content_hash=(SELECT content_hash FROM orphan WHERE id=node_runs.node_id)
 AND EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=n.id))
 WHERE node_id IN (SELECT id FROM orphan)
 AND EXISTS(SELECT 1 FROM nodes n JOIN tower_nodes t ON t.node_id=n.id
 WHERE n.content_hash=(SELECT content_hash FROM orphan WHERE id=node_runs.node_id))`)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `DELETE FROM nodes
 WHERE content_hash IN (SELECT content_hash FROM refresh_affected)
 AND NOT EXISTS(SELECT 1 FROM tower_nodes WHERE node_id=nodes.id);
 DELETE FROM refresh_affected`)
	return err
}
