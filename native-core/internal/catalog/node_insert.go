package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

func insertNode(ctx context.Context, tx *sql.Tx, towerID int64, ordinal, representative int, node Node,
	hash, nameKey string, output flight.DeduplicationConfig) error {
	var id int64
	if ordinal == representative {
		value, err := nodeFlight(node)
		if err != nil {
			return err
		}
		// 正式表只存内容去重后的完整配置；冲突复用原行，保持稳定首个。
		canonical := node
		canonical.ContentHash = hash
		canonical.Name = strings.TrimSpace(node.Name)
		value.Name = canonical.Name
		config, err := json.Marshal(canonical)
		if err != nil {
			return err
		}
		err = tx.QueryRowContext(ctx, `INSERT INTO nodes(content_hash,name_key,output_key,config,name,type,server,port)
 VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(content_hash,name_key) DO UPDATE SET
 config=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.config ELSE nodes.config END,
 name=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.name ELSE nodes.name END,
 output_key=CASE WHEN NOT EXISTS(SELECT 1 FROM tower_nodes t WHERE t.node_id=nodes.id) THEN excluded.output_key ELSE nodes.output_key END
 RETURNING id`,
			hash, nameKey, flight.DeduplicationKey(value, output), string(config), canonical.Name, node.Type, node.Server, node.Port).Scan(&id)
		if err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, "UPDATE refresh_nodes SET final_node=? WHERE tower_id=? AND ordinal=?", id, towerID, ordinal); err != nil {
			return err
		}
	} else {
		if err := tx.QueryRowContext(ctx, "SELECT final_node FROM refresh_nodes WHERE tower_id=? AND ordinal=?", towerID, representative).Scan(&id); err != nil {
			return err
		}
	}
	// 不丢重复来源、原始名称和 rawInput；不为来源差异创建重复正式节点。
	source, err := json.Marshal(struct {
		Source   json.RawMessage `json:"source"`
		Name     string          `json:"name"`
		RawInput string          `json:"rawInput"`
		Format   string          `json:"format"`
		Base64   bool            `json:"base64"`
	}{Source: node.Source, Name: node.Name, RawInput: node.RawInput, Format: node.SourceFormat, Base64: node.FromBase64})
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, "INSERT INTO tower_nodes(tower_id,ordinal,node_id,source,eligible) VALUES(?,?,?,?,1)", towerID, ordinal, id, string(source))
	return err
}
