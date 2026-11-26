//go:build integration

package catalog

import (
	"context"
	"encoding/json"
	"testing"
)

func TestCommitMeasuredReportsEachWritePhase(t *testing.T) {
	ctx := context.Background()
	store := reviewStore(t)
	nodes := reviewParse(t, "trojan://fixture@node.example:443#node")
	id, err := store.Add(ctx, "local:timed", "timed")
	requireResult(t, err)
	ticket, err := store.Begin(ctx, id)
	requireResult(t, err)
	requireResult(t, store.Stage(ctx, id, ticket.Generation, 0, nodes))

	timings, err := store.CommitMeasured(ctx, Refresh{
		ID:         id,
		Generation: ticket.Generation,
		Count:      len(nodes),
		Usage:      json.RawMessage(`null`),
		Warnings:   json.RawMessage(`[]`),
	})
	requireResult(t, err)
	for _, phase := range []string{
		"prepare", "validate", "classify", "replace_nodes", "regions", "cleanup", "commit",
	} {
		if _, ok := timings[phase]; !ok {
			t.Fatalf("missing commit phase timing %q: %#v", phase, timings)
		}
	}
}
