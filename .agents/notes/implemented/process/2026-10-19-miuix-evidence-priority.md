# Agent Note: 本项目 Miuix 资料获取优先级

Status: implemented

## Problem

用户要求 ohmydrift 的 Miuix 资料优先使用已安装的 miuix Skill，EXA MCP 改为降级查询，不修改全局规则。

## Decision

项目 AGENTS.md 与 docs/references.md 统一为：先用 miuix Skill 及必要引用；资料不足、版本冲突、Skill 不可用或需要补齐精确 API 证据时再用 EXA MCP。网络补证仍查项目锁定版本，EXA 无法取得必要资料时才用浏览器。不为已有充分证据重复联网，不通过 curl 绕过优先级。

## Alternatives considered

- 每次先联网：可直接取得源码，但重复查询并绕过用户提供的 Skill，不采用。
- 修改全局 Skill 或全局记忆：能影响所有项目，但超出本次仅针对 ohmydrift 的范围，不采用。

## Consequences

两处项目规则同步，保留版本核实与 Klibs 新依赖选型要求，不恢复暂停的 UI 实施。Skill 内容不是编译或真机证据；版本不匹配和 API 缺口仍需补证，不能以优先使用 Skill 为由猜参数。

本地结构检查曾把所有 Skill/技能字样判为无关引用，导致新增规则失败；现只移除泛词禁令，保留无关框架、旧项目名及本地链接边界检查。新增一项回归测试，同时验证合法 miuix Skill 引用通过、原有无关引用仍被拒绝。
