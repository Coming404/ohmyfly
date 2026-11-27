// 刷新进度随请求上下文传递，不持有数据库连接。
package catalog

import "context"

type refreshProgressKey struct{}

// WithRefreshProgress 为移动端刷新请求安装进度回调。
func WithRefreshProgress(ctx context.Context, report func(string, int, int)) context.Context {
	return context.WithValue(ctx, refreshProgressKey{}, report)
}

// reportRefresh 只发布已完成的记录数，不推测耗时百分比。
func reportRefresh(ctx context.Context, stage string, completed, total int) {
	if report, ok := ctx.Value(refreshProgressKey{}).(func(string, int, int)); ok {
		report(stage, completed, total)
	}
}
