// 导出当前正式节点为 Mihomo proxies YAML；只读取已选节点的规范化配置。
package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"

	"go.yaml.in/yaml/v3"
)

func (s *Store) ExportYAML(ctx context.Context, ids []int64) (string, error) {
	if len(ids) == 0 || len(ids) > 100 {
		return "", errors.New("export requires 1..100 nodes")
	}
	tx, err := s.readDB.BeginTx(ctx, &sql.TxOptions{ReadOnly: true})
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	encoded, err := json.Marshal(ids)
	if err != nil {
		return "", err
	}
	rows, err := tx.QueryContext(ctx, "SELECT config FROM nodes WHERE id IN (SELECT value FROM json_each(?)) ORDER BY id", string(encoded))
	if err != nil {
		return "", err
	}
	defer rows.Close()
	proxies := make([]map[string]any, 0, len(ids))
	for rows.Next() {
		var raw string
		if err = rows.Scan(&raw); err != nil {
			return "", err
		}
		var value map[string]any
		if err = json.Unmarshal([]byte(raw), &value); err != nil {
			return "", err
		}
		if fields, ok := value["fields"].(map[string]any); ok {
			if name, ok := value["name"].(string); ok {
				fields["name"] = name
			}
			value = fields
		}
		proxies = append(proxies, value)
	}
	if err = rows.Err(); err != nil {
		return "", err
	}
	if len(proxies) != len(ids) {
		return "", ErrStale
	}
	out, err := yaml.Marshal(map[string]any{"proxies": proxies})
	if err != nil {
		return "", err
	}
	return string(out), tx.Commit()
}
