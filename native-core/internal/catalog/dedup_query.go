package catalog

import (
	"context"
	"database/sql"
)

// 基础去重已经在入库事务完成；汇总规则只对正式节点追加稳定首个分组。
const dedupCandidates = `WITH source_order AS (
 SELECT *,row_number() OVER(PARTITION BY node_id ORDER BY tower_id,ordinal) AS rank
 FROM tower_nodes WHERE eligible=1
), available AS (
 SELECT n.id,n.output_key,t.tower_id,t.ordinal
 FROM nodes n JOIN source_order t ON t.node_id=n.id AND t.rank=1
), representatives AS (
 SELECT *,first_value(id) OVER(PARTITION BY
 CASE WHEN output_key='' THEN json_array('node',id) ELSE json_array('output',output_key) END
 ORDER BY tower_id,ordinal,id) AS representative_id FROM available
), source_members AS (
 SELECT r.representative_id,t.tower_id,t.ordinal,t.node_id,t.source
 FROM representatives r JOIN tower_nodes t ON t.node_id=r.id WHERE t.eligible=1
), candidates AS (SELECT DISTINCT representative_id AS id FROM representatives) `

const basicCandidates = `WITH candidates AS (
 SELECT n.id FROM nodes n WHERE ` + effectiveNodes + `) `

func candidateQuery(ctx context.Context, tx *sql.Tx, filtered bool) (string, error) {
	var mode string
	if err := tx.QueryRowContext(ctx, "SELECT json_extract(output_rules,'$.mode') FROM catalog_meta WHERE id=1").Scan(&mode); err != nil {
		return "", err
	}
	query := dedupCandidates
	if mode == "none" {
		query = basicCandidates
	}
	if filtered {
		var groupID int64
		var status string
		if err := tx.QueryRowContext(ctx, "SELECT group_id,status FROM filter_scope WHERE id=1").Scan(&groupID, &status); err != nil {
			return "", err
		}
		if groupID == -1 && status == "all" {
			query += `, matched AS (SELECT c.id FROM candidates c JOIN filter_results r ON r.node_id=c.id WHERE r.eligible=1) `
		} else {
			query += `, base_matched AS (SELECT c.id FROM candidates c JOIN filter_results r ON r.node_id=c.id WHERE r.eligible=1),
 matched AS (SELECT b.id FROM base_matched b CROSS JOIN filter_scope s WHERE s.id=1
 AND (s.group_id=-1 OR (s.group_id=0 AND NOT EXISTS(SELECT 1 FROM node_groups g WHERE g.node_id=b.id AND g.group_id IS NOT NULL))
 OR EXISTS(SELECT 1 FROM node_groups g WHERE g.node_id=b.id AND g.group_id=s.group_id))
 AND (s.status='all' OR coalesce((SELECT status FROM node_runs r WHERE r.node_id=b.id ORDER BY r.id DESC LIMIT 1),'none')=s.status)) `
		}
	}
	return query, nil
}
