// 完整节点解码和键计算在最终写事务之外分批完成，写事务仅保存已准备数据。
package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"strings"
)

type preparedRules struct{ tower, output string }
type refreshInput struct {
	ordinal int
	payload string
	trusted bool
}
type preparedNode struct {
	Ordinal  int    `json:"ordinal"`
	Hash     string `json:"hash"`
	Name     string `json:"name"`
	Advanced string `json:"advanced"`
	Eligible bool   `json:"eligible"`
	Config   string `json:"config"`
	Output   string `json:"output"`
	Source   string `json:"source"`
}

func (s *Store) prepareRefresh(ctx context.Context, r Refresh) (preparedRules, error) {
	var rules preparedRules
	err := s.readDB.QueryRowContext(ctx, `SELECT dedup_rules,output_rules FROM towers,catalog_meta WHERE towers.id=? AND catalog_meta.id=1`, r.ID).Scan(&rules.tower, &rules.output)
	if errors.Is(err, sql.ErrNoRows) {
		return rules, ErrStale
	}
	if err != nil {
		return rules, err
	}
	var tower, output flight.DeduplicationConfig
	if err = json.Unmarshal([]byte(rules.tower), &tower); err != nil {
		return rules, err
	}
	if err = json.Unmarshal([]byte(rules.output), &output); err != nil {
		return rules, err
	}
	for next := 0; next < r.Count; {
		inputs, err := s.refreshInputs(ctx, r, next)
		if err != nil {
			return rules, err
		}
		if len(inputs) == 0 {
			return rules, errors.New("incomplete catalog refresh")
		}
		values := make([]preparedNode, 0, len(inputs))
		for _, input := range inputs {
			if input.ordinal != next || next >= r.Count {
				return rules, errors.New("noncontiguous catalog refresh")
			}
			value, err := prepareNode(input, tower, output)
			if err != nil {
				return rules, err
			}
			values = append(values, value)
			next++
		}
		if err = s.savePrepared(ctx, r, values); err != nil {
			return rules, err
		}
	}
	return rules, nil
}

func (s *Store) refreshInputs(ctx context.Context, r Refresh, start int) ([]refreshInput, error) {
	rows, err := s.readDB.QueryContext(ctx, `SELECT ordinal,payload,EXISTS(SELECT 1 FROM refresh_origin o
 WHERE o.tower_id=n.tower_id AND o.generation=n.generation AND o.ordinal=n.ordinal)
 FROM refresh_nodes n WHERE tower_id=? AND generation=? AND ordinal>=? AND complete=1 ORDER BY ordinal`, r.ID, r.Generation, start)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []refreshInput{}
	bytes := 0
	for rows.Next() {
		var input refreshInput
		if err = rows.Scan(&input.ordinal, &input.payload, &input.trusted); err != nil {
			return nil, err
		}
		if len(result) > 0 && bytes+len(input.payload) > ParseBatchBytes {
			break
		}
		result = append(result, input)
		bytes += len(input.payload)
		if bytes >= ParseBatchBytes {
			break
		}
	}
	return result, rows.Err()
}

func prepareNode(input refreshInput, tower, output flight.DeduplicationConfig) (preparedNode, error) {
	var node Node
	if err := json.Unmarshal([]byte(input.payload), &node); err != nil {
		return preparedNode{}, err
	}
	value, err := nodeFlight(node)
	if err != nil {
		return preparedNode{}, err
	}
	hash := node.ContentHash
	// 只有从Go解析会话直接复制的节点才能复用哈希；外部stage仍在边界重新验证。
	if !input.trusted {
		hash = flight.ContentHash(value)
	}
	if hash == "" {
		return preparedNode{}, errors.New("invalid node content hash")
	}
	source, err := json.Marshal(struct {
		Source   json.RawMessage `json:"source"`
		Name     string          `json:"name"`
		RawInput string          `json:"rawInput"`
		Format   string          `json:"format"`
		Base64   bool            `json:"base64"`
	}{node.Source, node.Name, node.RawInput, node.SourceFormat, node.FromBase64})
	if err != nil {
		return preparedNode{}, err
	}
	name := strings.TrimSpace(node.Name)
	advanced := flight.DeduplicationKey(value, tower)
	node.Name = name
	node.ContentHash = hash
	value.Name = name
	config, err := json.Marshal(node)
	if err != nil {
		return preparedNode{}, err
	}
	return preparedNode{input.ordinal, hash, name, advanced, node.Eligible, string(config), flight.DeduplicationKey(value, output), string(source)}, nil
}

func (s *Store) savePrepared(ctx context.Context, r Refresh, values []preparedNode) error {
	data, err := json.Marshal(values)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, r.ID, r.Generation); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `UPDATE refresh_nodes SET content_hash=json_extract(j.value,'$.hash'),
 name=json_extract(j.value,'$.name'),advanced_key=json_extract(j.value,'$.advanced'),eligible=json_extract(j.value,'$.eligible')
 FROM json_each(?) j WHERE tower_id=? AND generation=? AND ordinal=json_extract(j.value,'$.ordinal')`, string(data), r.ID, r.Generation)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO refresh_prepared(tower_id,generation,ordinal,config,output_key,source)
 SELECT ?,?,json_extract(value,'$.ordinal'),json_extract(value,'$.config'),json_extract(value,'$.output'),json_extract(value,'$.source')
 FROM json_each(?) WHERE true ON CONFLICT(tower_id,generation,ordinal) DO UPDATE SET
 config=excluded.config,output_key=excluded.output_key,source=excluded.source`, r.ID, r.Generation, string(data))
	if err != nil {
		return err
	}
	return tx.Commit()
}
