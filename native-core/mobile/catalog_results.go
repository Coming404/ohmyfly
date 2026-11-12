package mobile

import (
	"context"
	"errors"

	"github.com/Coming404/ohmyfly/native-core/internal/catalog"
)

func callResults(ctx context.Context, s *catalog.Store, r catalogRequest) (any, error) {
	switch r.Action {
	case "taskCreate":
		return s.CreateTask(ctx, r.Mode, r.Settings, r.NodeIDs)
	case "taskAddNodes":
		return map[string]bool{"ok": true}, s.AddTaskNodes(ctx, r.ID, r.NodeIDs)
	case "taskUpdate":
		return map[string]bool{"ok": true}, s.UpdateTask(ctx, r.ID, r.Status, r.Reason)
	case "task":
		return s.Task(ctx, r.ID)
	case "runUpdate":
		return map[string]bool{"ok": true}, s.UpdateRun(ctx, r.ID, r.Status, r.Reason)
	case "metricWrite":
		return map[string]bool{"ok": true}, s.WriteMetric(ctx, r.Metric)
	case "platformWrite":
		return map[string]bool{"ok": true}, s.WritePlatform(ctx, r.Platform)
	case "results":
		return s.Results(ctx, r.NodeID, r.TaskID, r.Anchor, r.Limit)
	case "measurements":
		return s.Measurements(ctx, r.NodeIDs, r.Revision)
	default:
		return nil, errors.New("unknown results operation")
	}
}
