# 目录任务与测量结果契约

## 事实源与 schema

`native-core/internal/catalog` 的 SQLite 是目录、任务和结果的事实源，`mobile.Catalog` 以 JSON 暴露 API，共享层 `CatalogResults` 调用并解码。结果持久化属于数据库第1步；任务列表恢复、网络执行与后台调度留在第3步。

当前 `PRAGMA user_version=3`。v2 升至 v3 时新增 `tower_daily_summary`，按日期保存全库航班数和可空的已用流量。重读目录时更新当天快照，返回最近七个自然日；只有真实观测才参与绘图。v1 至 v2 在事务内更新国家名称识别结果，保留节点身份、手动地区及测量历史，详见[节点地区](towers-flights.md#节点地区)。任务与测量 schema 包含：

| 表 | 内容与关联 |
| --- | --- |
| `flight_tasks` | 模式、完整设置 JSON 快照、任务状态、创建/开始/结束时间、原因 |
| `node_runs` | 稳定 run ID、task ID、node ID、执行状态与时间；外键关联任务和正式节点 |
| `metric_results` | 主键 `(run_id, kind)`，各指标状态、可空值、测量时间、原因 |
| `platform_results` | 主键 `(run_id, platform)`，平台标识、状态、地区、检测时间、原因 |

`node_runs_history(node_id,id DESC)` 与 `node_runs_task(task_id,id)` 支持按节点历史或任务成员读取。每次任务创建新的 run；同一次 run 的阶段只允许由 `running` 更新为终态，已终结阶段不可覆盖。重新测量创建新任务，历史不被摘要覆盖。

旧开发 v1 如果缺少 `catalog_meta.metrics_revision`，打开会返回 `catalog v1 schema is incomplete; explicit upgrade decision required`，文件保持不变。结构完整的 v1 会先更新地区识别，再升级至 v3；v2 仅新增每日汇总表。版本大于3拒绝打开。缺少结果表结构的早期开发库仍须另行确定保留数据的升级方案。

## 时间、数值和快照

- 所有 `createdAt`、`startedAt`、`finishedAt`、`measuredAt`、`checkedAt`、塔台 `lastAttemptAt` / `lastSuccessAt` 都是 Unix 毫秒，不是 Go duration 纳秒。
- `latency.value` 为毫秒（ms）；`download.value` 与 `upload.value` 为字节/秒（B/s）。数据库没有单独的单位列，写入方按此契约转换，展示方负责格式化；不能将 bit/s 当 B/s。
- `succeeded` 指标必须有非负有限值及测量时间。成功值 `0` 与未测 `null` 不同。非成功指标必须 `value=null`；`running` 的时间为 `null`，终态时间必须从 run 开始到当前时刻之间。
- 任务模式为 `Latency` / `Download` / `Full`，分别允许延迟、延迟+下载、延迟+下载+上传。模式不自动执行网络测试。
- Kotlin `SpeedSettings` / `SpeedMode` 可序列化，`catalogJson.encodeDefaults=true`，创建任务保存包含默认值的完整传入配置快照。Go 要求快照为非空 JSON 对象、`mode` 一致、编码最多65536字节；不重新校验 Kotlin 全部设置项。

## 状态与取消

任务和节点状态：`queued → running → succeeded|failed|cancelled`；任务也可从 queued 直接失败或取消。节点成功要求本模式全部指标成功、已记录平台检测全部为 `unlocked` 或 `locked`；任务成功要求全部成员成功。

指标状态：`running`、`succeeded`、`failed`、`cancelled`、`interrupted`。平台状态：`running`、`unlocked`、`locked`、`failed`、`cancelled`、`interrupted`。平台缺席表示没有记录，不等同于 `locked`。`unlocked` 只表示此次平台检测的网络/地区结果，不能推导账号或全部片库可用。

取消/失败任务会在同一事务中终结其 queued/running 节点与 running 子阶段，保留已经完成的指标和平台结果。取消/失败单节点只关闭它自己的 running 子阶段。重新打开数据库会将未完成任务、节点和 running 阶段标为 `interrupted`，保留已成功数据和原测量时间；`interrupted` 由打开恢复过程产生，写入 API 不接受调用方直接设置。

Kotlin 写入仍使用 `withContext(worker)` 并原样传播数据库错误和调用方取消；返回途中取消可能使提交状态未知，取消不能解释为数据库回滚。已取得成功提交响应后，只在目录现有 `SupervisorJob + Main` scope 发起摘要读取，不再等待显示刷新，因此正常返回的任务 ID 和写入成功不会被显示读取失败夺走。摘要刷新本身的 `CancellationException` 继续传播，不包装为 IO 失败。

## 版本与可见页刷新

- `revision` 是目录结构/资格/规则的修订号，变化后旧页面和筛选票据失效，桥接返回 `stale:true`。
- `metricsRevision` 是独立的结果修订号，创建任务、增添成员、状态或阶段写入、重启中断恢复会推进。测量写入不增加目录 revision，现有筛选票据继续可用。
- `measurements` 和页面摘要在同一读取事务中返回结果版本与数据；一个批次最多100个互不重复且存在的 node ID。
- 共享层按页面 epoch、目录 revision、筛选 token、当前 node ID 有序集合确认响应身份，同时用刷新序号隔离并发请求。旧页响应不能更新新页数据或抬高其结果版本；可见集合改变时重读当前集合。页面正在加载/不可见/后台时保留 `measurementsPending`，页面（含缓存页）发布后补读；前台恢复沿用目录 reload。
- 刷新错误独立存放在 `CatalogState.measurementsFailure`，`measurementsPending=true` 保持待重读，不改写已提交操作的成功状态。重新调用 `refreshMeasurements` 或重新读取页面/恢复前台只读取数据库，不重发写入。成功后清除摘要错误与待重读标记。
- 列表每批100条、每列表最多5批，所有列表共享 `50 * 1024 * 1024` 字节估算 LRU 预算；不是预分配50 MiB。单批超预算不缓存，平台摘要字符串计入估算。后台清空、内存压力 trim 保留当前批；结果版本推进时淘汰旧版本航班缓存。

## 摘要与完整历史

节点摘要 `measurements`：

```json
{
  "nodeId": 17,
  "latestRun": null,
  "metrics": {
    "latency": {"latest": null, "valid": null}
  },
  "platforms": [
    {"platform": "custom_stream", "status": "unlocked", "region": "JP", "checkedAt": 1794480000000}
  ]
}
```

`latestRun` 是最大 run ID 的轻量状态（其 metrics/platforms 数组为空）；指标 `latest` 是最近尝试，`valid` 是最近成功，各自保留 run ID、状态、值、时间和原因。失败的新延迟不抹去以前的成功值；只测延迟也不抹去以前的下载/上传。

`platforms` 是数组，按平台 ID 稳定排序，每个平台仅保留该节点的最近状态、地区和时间。Go `PlatformSummary` 与 Kotlin `CatalogPlatformSummary` 字段相同。没有检测记录返回 `[]`，不是按状态计数的对象；UI 可从数组派生计数并按 `platform` 匹配图标。平台 ID 是数据事实，允许 `^[a-z0-9][a-z0-9_-]{0,63}$`，不设平台白名单。每 run 最多32个平台，region 最多64字节、reason 最多512字节；只有 unlocked/locked 可携带非空地区，running 的 checkedAt 为 null。不同任务出现过的平台都可进入摘要，所以摘要平台种类数不受单 run 的32限制。

最近指标/平台按 `coalesce(测量时间,run.started_at)` 倒序，同时间按 run ID 倒序；`valid` 只选成功记录。完整历史始终由 `results` 返回各 run 的全部阶段记录，不从摘要反推。

这些摘要通过一批节点的集合查询读取（不是逐卡请求/N+1）。当前指标和平台 SQL 仍对本批节点的历史做窗口排序，成本随这些节点历史增长；基础页面索引检查不证明多年历史摘要成本恒定。本阶段未增加最新结果投影或有界历史查询优化。

删除一个来源时，共享正式节点及其历史仍保留；最后来源删除使正式节点删除时，run 与结果外键级联删除，任务记录保留。相同 ContentHash 节点合并会迁移 run ID 与历史；不同连接内容的新节点不继承旧结果。

## JSON API

入口为 `mobile.OpenCatalog(path)` / `mobile.Catalog(request)`。请求带 `action`；成功直接返回对象，错误返回 `{"error":"..."}`，目录修订失效另含 `"stale":true`。不存在 HTTP 状态码层。ID 与版本均为整数，Kotlin 使用 Long。写操作未提供重试幂等键，成功写入后只重读，不因摘要失败重发。

| action | 请求字段 | 成功返回 |
| --- | --- | --- |
| `taskCreate` | `mode`, `settings`, `nodeIds`（1..100个有效节点） | Task：`id,mode,settings,status,createdAt,startedAt,finishedAt,reason` |
| `taskAddNodes` | `id` 为 task ID, `nodeIds`（1..100） | `{"ok":true}`；仅 queued 任务可补成员，拒绝重复成员 |
| `taskUpdate` | `id` 为 task ID, `status`, `reason` | `{"ok":true}` |
| `task` | `id` 为 task ID | Task |
| `runUpdate` | `id` 为 run ID, `status`, `reason` | `{"ok":true}` |
| `metricWrite` | `metric:{runId,kind,status,value,measuredAt,reason}` | `{"ok":true}`；任务和 run 必须 running |
| `platformWrite` | `platform:{runId,platform,status,region,checkedAt,reason}` | `{"ok":true}`；任务和 run 必须 running |
| `results` | `nodeId` 与 `taskId` 恰选一个正整数；`anchor>=0`, `limit:1..100` | `{"metricsRevision":整数,"runs":[NodeRun...]}` |
| `measurements` | `nodeIds`（0..100，唯一且存在）, `revision` | `{"revision":整数,"metricsRevision":整数,"nodes":[摘要...]}`，保持请求 ID 次序 |

`NodeRun` 包含 `id,taskId,nodeId,status,taskStatus,startedAt,finishedAt,reason,metrics,platforms`。历史按 run ID 逆序，`anchor=0` 从最新开始，下一批传上一批最后一个 ID（严格 `< anchor`），空数组表示结束；不使用 offset。Kotlin `readCatalogResults` 默认 limit=100，原生 JSON 调用方必须显式传 limit。历史子结果使用完整 Metric / PlatformResult（含 runId、reason），平台摘要只保留图标所需的四个字段。

塔台页面还返回 `warnings`、`lastAttemptAt`、`lastSuccessAt`：Begin 记录最近尝试时间，成功 Commit 才推进成功时间；失败保留旧节点与上次成功时间。默认输出去重 none 使用 nodes 与来源 EXISTS，筛选提交保存 match_count，后续分页复用；目录变化仍作废筛选票据。
