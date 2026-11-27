// 移动端批次通过独立请求取消原生计算；取消操作不等待 SQLite 写锁。
package mobile

import (
	"context"
	"errors"
	"sync"

	"github.com/Coming404/ohmyfly/native-core/internal/catalog"
)

type catalogOperation struct {
	ctx       context.Context
	cancel    context.CancelFunc
	stage     string
	completed int
	total     int
}

var operationMu sync.Mutex
var operationSequence int64
var catalogOperations = map[int64]*catalogOperation{}

// beginCatalogOperation 在来源开始处理时注册，覆盖解析、暂存与提交。
func beginCatalogOperation() int64 {
	operationMu.Lock()
	defer operationMu.Unlock()
	operationSequence++
	id := operationSequence
	ctx, cancel := context.WithCancel(context.Background())
	op := &catalogOperation{cancel: cancel}
	op.ctx = catalog.WithRefreshProgress(ctx, func(stage string, completed, total int) {
		operationMu.Lock()
		defer operationMu.Unlock()
		op.stage, op.completed, op.total = stage, completed, total
	})
	catalogOperations[id] = op
	return id
}

// catalogOperationContext 保证被取消的来源不能继续启动新的数据库工作。
func catalogOperationContext(id int64) (context.Context, error) {
	operationMu.Lock()
	defer operationMu.Unlock()
	op := catalogOperations[id]
	if op == nil {
		return nil, errors.New("catalog operation ended")
	}
	return op.ctx, op.ctx.Err()
}

// controlCatalogOperation 只操作内存状态，清理暂存数据由来源的 finally 完成。
func controlCatalogOperation(action string, id int64) any {
	operationMu.Lock()
	defer operationMu.Unlock()
	op := catalogOperations[id]
	if action == "operationStatus" {
		if op == nil {
			return map[string]any{"phase": "", "completed": 0, "total": 0}
		}
		return map[string]any{"phase": op.stage, "completed": op.completed, "total": op.total}
	}
	if op != nil {
		op.cancel()
		if action == "operationEnd" {
			delete(catalogOperations, id)
		}
	}
	return map[string]bool{"ok": true}
}
