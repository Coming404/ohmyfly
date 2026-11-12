package catalog

import (
	"context"
	"crypto/rand"
	"database/sql"
	"errors"
	"fmt"
)

// 合并只增加可见塔台与来源的关系，复用来源原有刷新代际、去重和失败保留事务。
const sourceSchema = `
CREATE TABLE IF NOT EXISTS tower_sources (
 source_id INTEGER PRIMARY KEY REFERENCES towers(id) ON DELETE CASCADE,
 parent_id INTEGER NOT NULL REFERENCES towers(id) ON DELETE CASCADE,
 address TEXT NOT NULL,
 UNIQUE(parent_id,address), CHECK(source_id<>parent_id)
);
CREATE INDEX IF NOT EXISTS tower_sources_parent ON tower_sources(parent_id,source_id);
`

type SourceInput struct {
	Address string `json:"address"`
	Name    string `json:"name"`
}

type Source struct {
	ID            int64  `json:"id"`
	ParentID      int64  `json:"parentId"`
	Address       string `json:"address"`
	Name          string `json:"name"`
	Failure       string `json:"failure"`
	LastSuccessAt *int64 `json:"lastSuccessAt"`
}

// AddSources 在一个事务中创建批次身份，网络/文件读取在提交后进行。
func (s *Store) AddSources(ctx context.Context, inputs []SourceInput, merge bool, name string, parent int64) ([]Source, error) {
	// 请求字节上限由移动桥接校验；用户整批数量由设置与调度层控制。
	if len(inputs) == 0 {
		return nil, errors.New("invalid source batch size")
	}
	seen := make(map[string]bool, len(inputs))
	for _, input := range inputs {
		if input.Address == "" || len(input.Address) > 8192 || seen[input.Address] {
			return nil, errors.New("invalid or repeated source address")
		}
		seen[input.Address] = true
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	if parent != 0 {
		var exists bool
		if err = tx.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM towers WHERE id=? AND address LIKE 'local:merged:%')`, parent).Scan(&exists); err != nil {
			return nil, err
		}
		if !merge || !exists {
			return nil, ErrStale
		}
	}
	if merge && parent == 0 {
		if name == "" {
			name = "合并塔台"
		}
		// 父身份不携带任何来源凭据，也不参与网络请求。
		err = tx.QueryRowContext(ctx, `INSERT INTO towers(address,name) VALUES('local:merged:'||lower(hex(randomblob(16))),?) RETURNING id`, name).Scan(&parent)
		if err != nil {
			return nil, err
		}
	}
	result := make([]Source, 0, len(inputs))
	for _, input := range inputs {
		address := input.Address
		if merge {
			address = "local:source:" + rand.Text()
		}
		var id int64
		err = tx.QueryRowContext(ctx, `INSERT INTO towers(address,name) VALUES(?,?)
 ON CONFLICT(address) DO UPDATE SET name=CASE WHEN towers.name='' THEN excluded.name ELSE towers.name END RETURNING id`, address, input.Name).Scan(&id)
		if err != nil {
			return nil, err
		}
		owner := id
		if merge {
			owner = parent
			if _, err = tx.ExecContext(ctx, `INSERT INTO tower_sources(source_id,parent_id,address) VALUES(?,?,?)`, id, parent, input.Address); err != nil {
				return nil, err
			}
		}
		result = append(result, Source{ID: id, ParentID: owner, Address: input.Address, Name: input.Name})
	}
	if err = bump(ctx, tx); err != nil {
		return nil, err
	}
	return result, tx.Commit()
}

// Sources 有界读取真实来源；普通塔台同样返回一个来源，避免两套刷新入口。
func (s *Store) Sources(ctx context.Context, parent, anchor int64) ([]Source, error) {
	rows, err := s.readDB.QueryContext(ctx, `SELECT t.id,coalesce(s.parent_id,t.id),coalesce(s.address,t.address),t.name,t.failure,t.last_success_at
 FROM towers t LEFT JOIN tower_sources s ON s.source_id=t.id
 WHERE t.id>? AND ((?=0 AND coalesce(s.address,t.address) NOT LIKE 'local:%') OR s.parent_id=? OR (t.id=? AND t.address NOT LIKE 'local:merged:%'))
 ORDER BY t.id LIMIT 100`, anchor, parent, parent, parent)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []Source{}
	for rows.Next() {
		var source Source
		if err = rows.Scan(&source.ID, &source.ParentID, &source.Address, &source.Name, &source.Failure, &source.LastSuccessAt); err != nil {
			return nil, err
		}
		result = append(result, source)
	}
	return result, rows.Err()
}

// 合并汇总不相加不同套餐的用量；来源失败、时间和节点数直接从独立事实读取。
func sourceOverview(ctx context.Context, tx *sql.Tx, tower *TowerSummary) error {
	if tower.SourceKind != "merged" {
		return nil
	}
	var failed int
	err := tx.QueryRowContext(ctx, `SELECT count(*),coalesce(sum(t.failure<>''),0),max(t.last_attempt_at),
 CASE WHEN count(t.last_success_at)=count(*) THEN min(t.last_success_at) END
 FROM tower_sources s JOIN towers t ON t.id=s.source_id WHERE s.parent_id=?`, tower.ID).
		Scan(&tower.SourceCount, &failed, &tower.LastAttemptAt, &tower.LastSuccessAt)
	if failed > 0 {
		tower.Failure = fmt.Sprintf("%d 个来源更新失败，已保留上次成功数据", failed)
	}
	return err
}
