package mobile

import (
	"context"
	"encoding/json"
	"errors"
	"sync"

	"github.com/Coming404/ohmyfly/native-core/internal/catalog"
	"github.com/Coming404/ohmyfly/native-core/internal/flight"
)

// 生命周期独占，普通请求共享；解析计算不再阻塞其他目录调用。
var catalogMu sync.RWMutex
var catalogStore *catalog.Store

// OpenCatalog 打开平台私有目录中的目录数据库，失败通过 JSON 明确返回。
func OpenCatalog(path string) string {
	catalogMu.Lock()
	defer catalogMu.Unlock()
	if catalogStore != nil {
		return encodeError(errors.New("catalog already open"))
	}
	store, err := catalog.Open(context.Background(), path)
	if err != nil {
		return encodeError(errors.New("无法打开目录数据库"))
	}
	catalogStore = store
	return `{"ok":true}`
}

type catalogRequest struct {
	OperationID  int64                      `json:"operationId"`
	GroupID      int64                      `json:"groupId"`
	Scope        catalog.FlightScope        `json:"scope"`
	References   []catalog.ParsedReference  `json:"references"`
	Sources      []catalog.SourceInput      `json:"sources"`
	Merge        bool                       `json:"merge"`
	Mode         string                     `json:"mode"`
	Settings     json.RawMessage            `json:"settings"`
	NodeIDs      []int64                    `json:"nodeIds"`
	NodeID       int64                      `json:"nodeId"`
	TaskID       int64                      `json:"taskId"`
	Status       string                     `json:"status"`
	Reason       string                     `json:"reason"`
	Limit        int                        `json:"limit"`
	Metric       catalog.Metric             `json:"metric"`
	Platform     catalog.PlatformResult     `json:"platform"`
	Dedup        catalog.DedupSettings      `json:"dedup"`
	Rule         flight.DeduplicationConfig `json:"rule"`
	Action       string                     `json:"action"`
	ID           int64                      `json:"id"`
	Generation   int64                      `json:"generation"`
	Address      string                     `json:"address"`
	Name         string                     `json:"name"`
	FilterTicket catalog.FilterTicket       `json:"filterTicket"`
	FilterToken  int64                      `json:"filterToken"`
	Decisions    []catalog.FilterDecision   `json:"decisions"`
	Failure      string                     `json:"failure"`
	Kind         string                     `json:"kind"`
	Anchor       int64                      `json:"anchor"`
	Backwards    bool                       `json:"backwards"`
	Revision     int64                      `json:"revision"`
	Start        int                        `json:"start"`
	Offset       int                        `json:"offset"`
	Payload      string                     `json:"payload"`
	Final        bool                       `json:"final"`
	Nodes        []catalog.Node             `json:"nodes"`
	Refresh      catalog.Refresh            `json:"refresh"`
	Observation  catalog.Summary            `json:"observation"`
}

// Catalog 仅暴露目录业务操作，不接受 SQL；跨语言批次同时限制条数和字节数。
func Catalog(request string) string {
	if len(request) > 1024*1024 {
		return encodeError(errors.New("目录批次超过 1 MiB"))
	}
	var r catalogRequest
	if err := json.Unmarshal([]byte(request), &r); err != nil {
		return encodeError(errors.New("目录请求无效"))
	}
	catalogMu.RLock()
	defer catalogMu.RUnlock()
	if catalogStore == nil {
		return encodeError(errors.New("目录数据库尚未打开"))
	}
	ctx := context.Background()
	// 清理操作必须能在来源取消后释放暂存记录。
	if r.OperationID != 0 && r.Action != "abort" && r.Action != "parseEnd" && r.Action != "status" {
		var err error
		ctx, err = catalogOperationContext(r.OperationID)
		if err != nil {
			return encodeError(err)
		}
	}
	value, err := callCatalog(ctx, r)
	if err != nil {
		if errors.Is(err, catalog.ErrStale) {
			return `{"error":"目录已更新，请重新加载","stale":true}`
		}
		return encodeError(errors.New("目录操作未完成，请重新读取目录确认状态"))
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return encodeError(errors.New("目录结果编码失败"))
	}
	return string(encoded)
}

func callCatalog(ctx context.Context, r catalogRequest) (any, error) {
	s := catalogStore
	switch r.Action {
	case "operationBegin":
		return map[string]int64{"id": beginCatalogOperation()}, nil
	case "operationCancel", "operationEnd", "operationStatus":
		return controlCatalogOperation(r.Action, r.ID), nil
	case "setDedup":
		return map[string]bool{"ok": true}, s.SetDedup(ctx, r.Dedup)
	case "setTowerDedup":
		return map[string]bool{"ok": true}, s.SetTowerDedup(ctx, r.ID, r.Rule)
	case "parseBegin":
		id, err := s.BeginParse(ctx)
		return map[string]int64{"id": id}, err
	case "parseChunk":
		return map[string]bool{"ok": true}, s.ParseChunk(ctx, r.ID, r.Start, r.Payload)
	case "parseFinish":
		return parseCatalogSubscription(ctx, r.ID)
	case "parsePage":
		return s.ParsedPage(ctx, r.ID, r.Kind, r.Start)
	case "parseRecord":
		return s.Parsed(ctx, r.ID, r.Kind, r.Start)
	case "queuePush":
		return map[string]bool{"ok": true}, s.QueuePush(ctx, r.ID, r.Start)
	case "queuePop":
		return s.QueuePop(ctx, r.ID, r.Start)
	case "parseEnd":
		return map[string]bool{"ok": true}, s.EndParse(ctx, r.ID)
	case "summary":
		return s.Summary(ctx)
	case "recordTrend":
		return map[string]bool{"ok": true}, s.RecordTrend(ctx, r.Observation)
	case "page":
		return s.Page(ctx, r.Kind, r.Anchor, r.Backwards, r.Revision, r.FilterToken)
	case "towerFlights":
		return s.TowerFlights(ctx, r.ID, r.Anchor, r.Revision)
	case "detail":
		return s.Detail(ctx, r.ID)
	case "addSources":
		sources, err := s.AddSources(ctx, r.Sources, r.Merge, r.Name, r.ID)
		return map[string]any{"sources": sources}, err
	case "sources":
		sources, err := s.Sources(ctx, r.ID, r.Anchor)
		return map[string]any{"sources": sources}, err
	case "add":
		id, err := s.Add(ctx, r.Address, r.Name)
		return map[string]int64{"id": id}, err
	case "resolveName":
		return map[string]bool{"ok": true}, s.ResolveTowerName(ctx, r.ID, r.Generation, r.Name)
	case "updateTower":
		return map[string]bool{"ok": true}, s.UpdateTower(ctx, r.ID, r.Address, r.Name)
	case "begin":
		return s.Begin(ctx, r.ID)
	case "stageParsed":
		return map[string]bool{"ok": true}, s.StageParsed(ctx, r.ID, r.Generation, r.Start, r.References)
	case "stage":
		return map[string]bool{"ok": true}, s.Stage(ctx, r.ID, r.Generation, r.Start, r.Nodes)
	case "chunk":
		return map[string]bool{"ok": true}, s.StageChunk(ctx, r.ID, r.Generation, r.Start, r.Offset, r.Payload, r.Final)
	case "commit":
		timings, err := s.CommitMeasured(ctx, r.Refresh)
		return map[string]any{"ok": true, "timingsMs": timings}, err
	case "status":
		return s.RefreshStatus(ctx, r.ID)
	case "abort":
		return map[string]bool{"ok": true}, s.Abort(ctx, r.ID, r.Generation)
	case "fail":
		return map[string]bool{"ok": true}, s.Fail(ctx, r.ID, r.Generation, r.Failure, r.Name)
	case "remove":
		return map[string]bool{"ok": true}, s.Remove(ctx, r.ID)
	case "removeTowers":
		return map[string]bool{"ok": true}, s.RemoveTowers(ctx, r.NodeIDs)
	case "nodeChange":
		return map[string]bool{"ok": true}, s.ChangeNodes(ctx, r.NodeIDs, r.Mode)
	case "selectionPage":
		ids, err := s.SelectionPage(ctx, r.Kind, r.Name, r.ID, r.Anchor, r.Revision, r.FilterToken)
		return map[string]any{"ids": ids}, err
	case "exportYAML":
		yamlText, err := s.ExportYAML(ctx, r.NodeIDs)
		return map[string]string{"yaml": yamlText}, err
	case "groups":
		return s.Groups(ctx, &r.FilterTicket)
	case "groupCreate":
		return s.CreateGroup(ctx, r.Name)
	case "groupRename":
		return map[string]bool{"ok": true}, s.RenameGroup(ctx, r.GroupID, r.Name)
	case "groupDelete":
		return map[string]bool{"ok": true}, s.DeleteGroup(ctx, r.GroupID)
	case "groupMove":
		return map[string]bool{"ok": true}, s.MoveGroup(ctx, r.GroupID, r.NodeIDs)
	case "beginFilter":
		return s.BeginFilter(ctx, r.Revision, r.Scope)
	case "filterInputs":
		inputs, err := s.FilterInputs(ctx, r.FilterTicket, r.Anchor)
		return map[string]any{"inputs": inputs}, err
	case "stageFilter":
		return map[string]bool{"ok": true}, s.StageFilter(ctx, r.FilterTicket, r.Decisions)
	case "commitFilter":
		count, err := s.CommitFilter(ctx, r.FilterTicket)
		return map[string]int64{"count": count}, err
	case "taskCreate", "taskAddNodes", "taskUpdate", "task", "runUpdate", "metricWrite", "platformWrite", "results", "measurements":
		return callResults(ctx, s, r)
	default:
		return nil, errors.New("unknown catalog operation")
	}
}
