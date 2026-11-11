package catalog

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
)

type Node struct {
	Name         string          `json:"name"`
	Type         string          `json:"type"`
	Server       string          `json:"server"`
	Port         int             `json:"port"`
	Fields       json.RawMessage `json:"fields"`
	RawInput     string          `json:"rawInput"`
	SourceFormat string          `json:"sourceFormat"`
	FromBase64   bool            `json:"fromBase64"`
	Fingerprint  string          `json:"fingerprint"`
	Source       json.RawMessage `json:"source"`
	Eligible     bool            `json:"eligible"`
}

type Ticket struct {
	ID         int64  `json:"id"`
	Generation int64  `json:"generation"`
	Address    string `json:"address"`
	Name       string `json:"name"`
}

type Refresh struct {
	ID         int64           `json:"id"`
	Generation int64           `json:"generation"`
	Name       string          `json:"name"`
	Usage      json.RawMessage `json:"usage"`
	Warnings   json.RawMessage `json:"warnings"`
	Count      int             `json:"count"`
}

var ErrStale = errors.New("catalog revision changed")

func (s *Store) Add(ctx context.Context, address, name string) (int64, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	var id int64
	err = tx.QueryRowContext(ctx, `INSERT INTO towers(address,name) VALUES(?,?)
 ON CONFLICT(address) DO UPDATE SET name=CASE WHEN towers.name='' THEN excluded.name ELSE towers.name END RETURNING id`, address, name).Scan(&id)
	if err != nil {
		return 0, err
	}
	if err = bump(ctx, tx); err != nil {
		return 0, err
	}
	return id, tx.Commit()
}

func (s *Store) Begin(ctx context.Context, id int64) (Ticket, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Ticket{}, err
	}
	defer tx.Rollback()
	var t Ticket
	err = tx.QueryRowContext(ctx, `UPDATE towers SET generation=generation+1 WHERE id=?
 RETURNING id,generation,address,name`, id).Scan(&t.ID, &t.Generation, &t.Address, &t.Name)
	if err != nil {
		return t, err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", id); err != nil {
		return t, err
	}
	return t, tx.Commit()
}

func checkTicket(ctx context.Context, tx *sql.Tx, id, generation int64) error {
	var current int64
	if err := tx.QueryRowContext(ctx, "SELECT generation FROM towers WHERE id=?", id).Scan(&current); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrStale
		}
		return err
	}
	if current != generation {
		return ErrStale
	}
	return nil
}

func (s *Store) Stage(ctx context.Context, id, generation int64, start int, nodes []Node) error {
	if len(nodes) > 100 || start < 0 {
		return errors.New("invalid catalog batch")
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	for i, node := range nodes {
		payload, err := json.Marshal(node)
		if err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, "INSERT INTO refresh_nodes(tower_id,generation,ordinal,payload) VALUES(?,?,?,?)", id, generation, start+i, string(payload)); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) Commit(ctx context.Context, r Refresh) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, r.ID, r.Generation); err != nil {
		return err
	}
	var count int
	if err = tx.QueryRowContext(ctx, "SELECT count(*) FROM refresh_nodes WHERE tower_id=? AND generation=? AND complete=1", r.ID, r.Generation).Scan(&count); err != nil {
		return err
	}
	if count != r.Count {
		return errors.New("incomplete catalog refresh")
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM tower_nodes WHERE tower_id=?", r.ID); err != nil {
		return err
	}
	// 资格取自刷新开始时的 Kotlin 设置快照，之后的设置变化不影响本次刷新。
	// 每次只解码一个完整节点，暂存批次不破坏最终替换的事务原子性。
	for ordinal := 0; ordinal < count; ordinal++ {
		var payload string
		if err = tx.QueryRowContext(ctx, "SELECT payload FROM refresh_nodes WHERE tower_id=? AND generation=? AND ordinal=?", r.ID, r.Generation, ordinal).Scan(&payload); err != nil {
			return err
		}
		var node Node
		if err = json.Unmarshal([]byte(payload), &node); err != nil {
			return err
		}
		if err = insertNode(ctx, tx, r.ID, ordinal, node); err != nil {
			return err
		}
	}
	_, err = tx.ExecContext(ctx, `UPDATE towers SET name=CASE WHEN name='' THEN ? ELSE name END,
 usage=?,warnings=?,failure='',committed_generation=? WHERE id=?`, r.Name, string(r.Usage), string(r.Warnings), r.Generation, r.ID)
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", r.ID); err != nil {
		return err
	}
	if err = collect(ctx, tx); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func insertNode(ctx context.Context, tx *sql.Tx, towerID int64, ordinal int, node Node) error {
	if !node.Eligible {
		return nil
	}
	// 变体键包括原始名称与完整配置；旧指纹仅用于第一阶段展示分组，不是 ContentHash。
	config, err := json.Marshal(struct {
		Name     string          `json:"name"`
		Type     string          `json:"type"`
		Server   string          `json:"server"`
		Port     int             `json:"port"`
		Fields   json.RawMessage `json:"fields"`
		RawInput string          `json:"rawInput"`
	}{
		Name: node.Name, Type: node.Type, Server: node.Server, Port: node.Port, Fields: node.Fields, RawInput: node.RawInput})
	if err != nil {
		return err
	}
	key := sha256.Sum256(config)
	var id int64
	err = tx.QueryRowContext(ctx, `INSERT INTO nodes(variant_key,legacy_fingerprint,config,name,type,server,port)
 VALUES(?,?,?,?,?,?,?) ON CONFLICT(variant_key) DO UPDATE SET variant_key=excluded.variant_key RETURNING id`,
		hex.EncodeToString(key[:]), node.Fingerprint, string(config), node.Name, node.Type, node.Server, node.Port).Scan(&id)
	if err != nil {
		return err
	}
	source, err := json.Marshal(struct {
		Source json.RawMessage `json:"source"`
		Format string          `json:"format"`
		Base64 bool            `json:"base64"`
	}{
		Source: node.Source, Format: node.SourceFormat, Base64: node.FromBase64})
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, "INSERT INTO tower_nodes VALUES(?,?,?,?,?)", towerID, ordinal, id, string(source), node.Eligible)
	return err
}

func (s *Store) Fail(ctx context.Context, id, generation int64, failure, name string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err = checkTicket(ctx, tx, id, generation); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "UPDATE towers SET failure=?,name=CASE WHEN name='' THEN ? ELSE name END WHERE id=?", failure, name, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, "DELETE FROM refresh_nodes WHERE tower_id=?", id); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) Remove(ctx context.Context, id int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, "DELETE FROM towers WHERE id=?", id); err != nil {
		return err
	}
	if err = collect(ctx, tx); err != nil {
		return err
	}
	if err = bump(ctx, tx); err != nil {
		return err
	}
	return tx.Commit()
}
