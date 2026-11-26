//go:build integration

package catalog

import (
	"context"
	"database/sql"
	"testing"
)

func TestSummaryReadsRemainPureAndTrendWritesAreExplicit(t *testing.T) {
	ctx := context.Background()
	store := reviewStore(t)
	reviewImport(t, store, "local:trend", reviewParse(t, "trojan://fixture@node.example:443#node"))

	summary, err := store.Summary(ctx)
	requireResult(t, err)
	if reviewCount(t, store, "tower_daily_summary") != 0 {
		t.Fatal("summary read wrote the daily trend")
	}
	requireResult(t, store.RecordTrend(ctx, summary))

	var flights int64
	var fuel sql.NullInt64
	requireResult(t, store.db.QueryRowContext(ctx, "SELECT flights,fuel_bytes FROM tower_daily_summary").Scan(&flights, &fuel))
	if flights != summary.Flights || fuel.Valid {
		t.Fatalf("stored daily trend = (%d,%v), summary = %+v", flights, fuel, summary)
	}
}
