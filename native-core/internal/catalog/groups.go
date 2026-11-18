// 航班自定义分组独立于来源；每个正式节点最多归属一个分组。
package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"strings"
	"unicode/utf8"
)

const groupSchema = `
CREATE TABLE IF NOT EXISTS flight_groups (
 id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS node_groups (
 node_id INTEGER PRIMARY KEY REFERENCES nodes(id) ON DELETE CASCADE,
 group_id INTEGER REFERENCES flight_groups(id) ON DELETE SET NULL,
 changed_revision INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS node_groups_group ON node_groups(group_id,node_id);
CREATE TABLE IF NOT EXISTS filter_scope (
 id INTEGER PRIMARY KEY REFERENCES filter_work(id) ON DELETE CASCADE,
 group_id INTEGER NOT NULL, status TEXT NOT NULL, metrics_revision INTEGER NOT NULL
);`

// FlightScope中0为默认“全部”分组，正值为自定义组；-1仅用于跨组查询。
type FlightScope struct {
	GroupID int64  `json:"groupId"`
	Status  string `json:"status"`
}

type FlightGroup struct {
	ID    int64  `json:"id"`
	Name  string `json:"name"`
	Count int64  `json:"count"`
}

type GroupSummary struct {
	Revision int64            `json:"revision"`
	Groups   []FlightGroup    `json:"groups"`
	Counts   map[string]int64 `json:"counts"`
}

// validateGroupName在桥接输入边界校验，名称唯一由数据库约束保证。
func validateGroupName(name string) (string, error) {
	name = strings.TrimSpace(name)
	if name == "" || utf8.RuneCountInString(name) > 16 {
		return "", errors.New("分组名称须为1至16个字符")
	}
	return name, nil
}

// CreateGroup持久化空分组，不预置演示分组。
func (s *Store) CreateGroup(ctx context.Context, name string) (FlightGroup, error) {
	name, err := validateGroupName(name)
	if err != nil {
		return FlightGroup{}, err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return FlightGroup{}, err
	}
	defer tx.Rollback()
	group := FlightGroup{Name: name}
	if err = tx.QueryRowContext(ctx, "INSERT INTO flight_groups(name) VALUES(?) RETURNING id", name).Scan(&group.ID); err != nil {
		return group, err
	}
	if err = bump(ctx, tx); err != nil {
		return group, err
	}
	return group, tx.Commit()
}

// RenameGroup只改变展示名，不改变节点归属。
func (s *Store) RenameGroup(ctx context.Context, id int64, name string) error {
	name, err := validateGroupName(name)
	if err != nil {
		return err
	}
	return s.changeGroup(ctx, id, "UPDATE flight_groups SET name=? WHERE id=?", name, id)
}

// DeleteGroup依靠关联外键解除归属，正式节点与来源不会被删除。
func (s *Store) DeleteGroup(ctx context.Context, id int64) error {
	if id <= 0 {
		return errors.New("invalid group id")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	// 删除也属于用户显式解除归属，后续合并不能用更早的分组决定覆盖它。
	if _, err = tx.ExecContext(ctx, `UPDATE node_groups SET group_id=NULL,
 changed_revision=(SELECT revision+1 FROM catalog_meta WHERE id=1) WHERE group_id=?`, id); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, "DELETE FROM flight_groups WHERE id=?", id)
	if err != nil {
		return err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if changed != 1 {
		return ErrStale
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

// changeGroup使组变更与目录修订递增处于同一事务。
func (s *Store) changeGroup(ctx context.Context, id int64, query string, args ...any) error {
	if id <= 0 {
		return errors.New("invalid group id")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, query, args...)
	if err != nil {
		return err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if changed != 1 {
		return ErrStale
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

// MoveGroup原子移动所选正式节点；任一节点已失效则整体拒绝，不部分成功。
func (s *Store) MoveGroup(ctx context.Context, groupID int64, ids []int64) error {
	if groupID < 0 || len(ids) == 0 {
		return errors.New("invalid group move")
	}
	encoded, err := json.Marshal(ids)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if groupID > 0 {
		var exists bool
		if err = tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM flight_groups WHERE id=?)", groupID).Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return ErrStale
		}
	}
	var count int
	err = tx.QueryRowContext(ctx, `SELECT count(*) FROM nodes n WHERE n.id IN (SELECT value FROM json_each(?)) AND `+effectiveNodes, string(encoded)).Scan(&count)
	if err != nil {
		return err
	}
	if count != len(ids) {
		return ErrStale
	}
	// NULL表示用户显式移回默认“全部”分组，仍保留操作顺序供同内容合并时判定。
	_, err = tx.ExecContext(ctx, `INSERT INTO node_groups(node_id,group_id,changed_revision)
 SELECT value,nullif(?,0),(SELECT revision+1 FROM catalog_meta WHERE id=1) FROM json_each(?) WHERE true
 ON CONFLICT(node_id) DO UPDATE SET group_id=excluded.group_id,changed_revision=excluded.changed_revision`, groupID, string(encoded))
	if err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

// Groups返回当前有效节点的全库计数；有筛选票据时按搜索/正则结果统计。
func (s *Store) Groups(ctx context.Context, ticket *FilterTicket) (GroupSummary, error) {
	result := GroupSummary{Groups: []FlightGroup{}, Counts: map[string]int64{"succeeded": 0, "failed": 0, "none": 0}}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	if ticket != nil {
		if err = checkFilter(ctx, tx, *ticket, true); err != nil {
			return result, err
		}
	}
	if err = tx.QueryRowContext(ctx, "SELECT revision FROM catalog_meta WHERE id=1").Scan(&result.Revision); err != nil {
		return result, err
	}
	query, err := candidateQuery(ctx, tx, false)
	if err != nil {
		return result, err
	}
	base := "candidates"
	if ticket != nil {
		// 名称和协议筛选影响所有计数；当前点击的分组和运行状态不截断其他分组。
		query += `, group_candidates AS (SELECT c.id FROM candidates c
 JOIN filter_results r ON r.node_id=c.id WHERE r.eligible=1) `
		base = "group_candidates"
	}
	rows, err := tx.QueryContext(ctx, query+`SELECT g.id,g.name,count(b.id) FROM flight_groups g
 LEFT JOIN node_groups m ON m.group_id=g.id LEFT JOIN `+base+` b ON b.id=m.node_id GROUP BY g.id ORDER BY g.id`)
	if err != nil {
		return result, err
	}
	for rows.Next() {
		var g FlightGroup
		if err = rows.Scan(&g.ID, &g.Name, &g.Count); err != nil {
			rows.Close()
			return result, err
		}
		result.Groups = append(result.Groups, g)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return result, err
	}
	var total int64
	err = tx.QueryRowContext(ctx, query+"SELECT count(*) FROM "+base).Scan(&total)
	if err != nil {
		return result, err
	}
	result.Counts["all"] = total
	// 状态来自每个节点最近一次真实运行；没有运行记录才属于未起飞。
	rows, err = tx.QueryContext(ctx, query+`SELECT coalesce((SELECT status FROM node_runs r WHERE r.node_id=b.id ORDER BY id DESC LIMIT 1),'none'),count(*) FROM `+base+` b GROUP BY 1`)
	if err != nil {
		return result, err
	}
	for rows.Next() {
		var status string
		var count int64
		if err = rows.Scan(&status, &count); err != nil {
			rows.Close()
			return result, err
		}
		result.Counts[status] = count
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return result, err
	}
	var defaultCount int64
	if err = tx.QueryRowContext(ctx, query+"SELECT count(*) FROM "+base+" b WHERE NOT EXISTS(SELECT 1 FROM node_groups m WHERE m.node_id=b.id AND m.group_id IS NOT NULL)").Scan(&defaultCount); err != nil {
		return result, err
	}
	result.Counts["default"] = defaultCount
	return result, tx.Commit()
}
