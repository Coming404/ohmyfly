package mobile

import (
	"context"
	"slices"
	"strings"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"github.com/Coming404/ohmyfly/native-core/internal/towerparse"
)

// 解析时逐节点写入私有 SQLite，桥接只返回统计与按需读取句柄。
func parseCatalogSubscription(ctx context.Context, id int64) (any, error) {
	index := 0
	stats, err := towerparse.Parse(catalogStore.ParseReader(ctx, id), func(value flight.Flight) error {
		node := subscriptionNode{Name: value.Name, Type: value.Type, Server: value.Server, Port: value.Port,
			Fields: jsonSafeValue(value.Fields).(map[string]any), RawInput: value.RawInput,
			SourceFormat: value.SourceFormat, FromBase64: value.FromBase64,
			ContentHash: flight.ContentHash(value)}
		err := catalogStore.SaveParsed(ctx, id, "node", index, node)
		index++
		return err
	})
	if err != nil {
		return nil, err
	}
	slices.SortFunc(stats.ProxyProviders, func(a, b towerparse.ProxyProvider) int { return strings.Compare(a.Name, b.Name) })
	for i, provider := range stats.ProxyProviders {
		value := subscriptionProvider{Name: provider.Name, URL: provider.URL, Fields: jsonSafeValue(provider.Fields).(map[string]any)}
		if err = catalogStore.SaveParsed(ctx, id, "provider", i, value); err != nil {
			return nil, err
		}
	}
	if err = catalogStore.ClearParseInput(ctx, id); err != nil {
		return nil, err
	}
	return map[string]any{"session": id, "nodeCount": index, "providerCount": len(stats.ProxyProviders),
		"subscription": subscriptionResult{Nodes: []subscriptionNode{}, ProxyProviders: []subscriptionProvider{},
			Candidates: stats.Candidates, Parsed: stats.Parsed, Skipped: stats.Skipped, YAML: stats.YAML, Links: stats.Links,
			Base64: stats.Base64, Base64Layers: stats.Base64Layers, FormatCounts: stats.FormatCounts, SkippedByReason: stats.SkippedByReason}}, nil
}
