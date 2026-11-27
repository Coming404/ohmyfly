// 交互请求登记后，导入在下一批写入前让行；等待期间不持有数据库连接。
package catalog

import (
	"context"
	"sync"
)

type interactionGate struct {
	mu     sync.Mutex
	active int
	idle   chan struct{}
}

// BeginInteraction 覆盖一次短交互写入，而非整个下载、解析或界面刷新生命周期。
func (s *Store) BeginInteraction() func() {
	gate := &s.interactions
	gate.mu.Lock()
	if gate.active == 0 {
		gate.idle = make(chan struct{})
	}
	gate.active++
	gate.mu.Unlock()
	return func() {
		gate.mu.Lock()
		defer gate.mu.Unlock()
		gate.active--
		if gate.active == 0 {
			close(gate.idle)
			gate.idle = nil
		}
	}
}

// WaitForInteraction 在事务外等待已经登记的交互请求，停止信号可立即结束等待。
func (s *Store) WaitForInteraction(ctx context.Context) error {
	for {
		if err := ctx.Err(); err != nil {
			return err
		}
		s.interactions.mu.Lock()
		idle := s.interactions.idle
		s.interactions.mu.Unlock()
		if idle == nil {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-idle:
		}
	}
}
