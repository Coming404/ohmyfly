package catalog

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

const candidates = dedupCandidates

const filteredCandidates = dedupCandidates + `,
 matched AS (SELECT c.id FROM candidates c JOIN filter_results r ON r.node_id=c.id WHERE r.eligible=1) `
