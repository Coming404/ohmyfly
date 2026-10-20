# Agent Note: 6.2 设置乐观即时反馈与保存失败恢复

Status: implemented

## Problem

PersistedSettings 目前等 DataStore 的 Flow 才更新界面；每次点击独立启动写入，校验还可能基于旧 composition 值。需要点击后同步反馈，保证连续操作按序、不丢字段、不被早期 Flow 或写入完成覆盖，并在保存失败时恢复可确认数据。

## Decision

- 保持 PersistedSettings 的 content 签名、String? 错误与 dismiss；新增仅随该 composition 生命周期存在的 SettingsSession，不增加全局 Store、DI、依赖或存储格式。
- 会话直接持有唯一可见 Compose 状态。每次点击以最新可见值执行变换并校验，成功后同步发布；单个协程按调用顺序将原变换交给既有 repository.update，仍在 DataStore 事务内基于最新磁盘值合并。
- 第一个待保存操作取消旧 Flow 订阅；保存期间不接受其发射，事务后用新订阅的 first 获取实际已确认值，队列排空后恢复正常订阅。取消检查阻止旧订阅迟到发射或旧会话完成回写；不以值相等推测版本，也不整份快照覆盖其它字段。
- 保存失败丢弃失败操作及尚未保存的后续操作，回到最后已确认值，明确提示重新选择重试；不自动重放可能依赖失败操作的变换。取消不报告 IO 失败。成功提交后若确认读取失败，显示只读失败页并阻止后续保存，而不谎称已经回滚磁盘。
- 路径、重试键变化创建新会话；LaunchedEffect 管理整个读写子作用域，旧回调失效。读取失败／未来 schema 不进入可写状态，不写回默认配置。

## Existing decisions audit

- [材质选择](../../implemented/feature/2026-10-20-material-selection.md)、[设置首页](../../implemented/feature/2026-10-19-settings-home.md)、[官方单选](../../implemented/feature/2026-10-19-preset-selection-popup.md)：部分重叠，本项接管这些笔记明确留给6.2的保存前即时状态；选择回调和控件不变。当前授权只改6.2专属笔记，旧篇与总文档的状态同步由集成方处理。
- [设置仓库与镜像](../../implemented/feature/2026-10-19-settings-mirrors.md)：部分重叠，沿用同路径仓库缓存、DataStore 事务、schema 1及错误上抛，不改 repository／Codec。
- 活跃 proposed／implemented 中无同主题协调器；其它导航、材质效果及构建提案与本项无关，不处理6.3。

## Alternatives considered

- 每次点击独立 launch 加计数：最短，但不能保证变换入事务顺序，失败的旧写入与迟到 Flow 仍可能盖掉最新 UI，因此不用。
- 保存最新完整 AppSettings 快照：同步反馈简单，但会丢失事务开始前已保存的其它字段，且会意外重试早期失败的选择，因此仍保存原始变换。
- 按已提交值过滤旧 Flow／等待相等回声：无需重新订阅，但值相等不等于版本相同，编解码规范化也可能导致永远等不到；采用 DataStore 新订阅读取当前值。
- 失败后重放剩余操作：能保住部分后续选择，但变换可能依赖已失败的删除／添加／重置；本项选择明确丢弃后缀，让用户在已确认值上重新选择。

## Consequences

- PersistedSettings保持原content签名，直接读取SettingsSession的Compose快照；不保留旧Flow镜像/独立写入scope或第二套状态链。初期部分交付入口仍调用已删接口，主代理接管后统一到`SettingsSession(repo.data, repo::update)`和单个`LaunchedEffect { session.run() }`生命周期。
- 观察器在 pending 非空时不得发布读取值：协程取消是协作式的，被 change() 取消的 collector 仍可能再交付一次旧存储值，覆盖待保存乐观值或回滚基线。该条件也是队列排空后恢复订阅的唯一写入点。
- 新增11项Kotlin测试（SettingsSessionTest 10项＋SettingsSessionStaleReadTest 1项），以coroutines-test虚拟时间/受控挂起覆盖同步反馈、按序变换与字段保留、迟到Flow、A→B→A、输入校验、失败后缀丢弃/重选、取消、读取/未来schema失败、生命周期隔离、提交后确认失败，以及取消期间迟到读取不得覆盖乐观快照。另新增3项Python接线守卫；Kotlin尚未运行。
- SettingsSession/SettingsUiState分别持有协调逻辑与界面快照，源文件均小于150行；不改DataStore模型/格式/实例缓存。用户重试通过重新选择；失败不自动重放有依赖的后续变换。
- 本地未执行Kotlin或实盘DataStore；真实协程调度、应用重建和最低系统行为需后续授权编译/真机验收。完整Python/结构/diff检查由主代理集成后统一执行。
