# Documentation
# docs/codex/Documentation.md

## 1. 当前阶段状态

当前仓库开发基线已进入：

# Phase 4：Trend Source Registry / Trend Inbox

本阶段从 Phase 3 的 Idea Lifecycle / Idea Workspace 继续向前推进，当前新增主线为：
- Trend Source Registry
- Default Trend Plan
- Trend ingest
- Trend AI 结构化分析
- Trend Inbox
- Trend -> Note / Idea 转化

Preference Learning 正式闭环、PWA 与移动端仍未进入当前主线。

### 1.1 Step 4.1 当前落地状态

当前仓库已完成 Step 4.1 的合同冻结：
- `trend_items` schema / index / foreign key 已落地
- Trend source / status / action 枚举已冻结
- Trend analysis payload 结构已冻结
- Trend repository 与未来 API DTO 形状已补齐

当前仓库尚未完成：
- real ingest
- Trend AI analyze runtime
- Trend Inbox controller / page
- Trend -> Note / Idea conversion

说明：
- Step 4.1 当前只冻结 persistence 与 contract
- 不提供可调用的 `/api/v1/trends/*` endpoint
- 不应把 Step 4.5/4.6 误记为已完成

### 1.2 Step 4.2 当前落地状态

当前仓库已完成 Step 4.2 的最小骨架：
- `TrendSourceRegistry` 已注册并解析 `HN` / `GITHUB`
- Default Trend Plan 已可从配置读取
- 已提供显式 trigger 入口 `POST /api/v1/trends/plans/default/trigger`
- trigger 当前只执行 `REGISTRY_ONLY`
- trigger 链路已写 `agent_traces`、结构化日志、最小 `tool_invocation_logs`

当前仓库在 Step 4.2 仍明确不做：
- 真实外部抓取
- `trend_items` 入库
- Trend AI 分析
- Trend Inbox 列表 / 动作
- Trend -> Note / Idea 转化

说明：
- Step 4.2 当前只完成来源注册与默认计划的显式触发骨架
- Step 4.3 才开始真实 ingest / normalize / dedupe / persistence

### 1.3 Step 4.3 当前落地状态

当前仓库已完成 Step 4.3 的最小 ingest 闭环：
- `POST /api/v1/trends/plans/default/trigger` 已从 `REGISTRY_ONLY` 升级为真实 `INGEST`
- 默认 plan 会同时触发 `HN` 与 `GITHUB` 两个 source 的最小候选拉取
- 候选会经过最小 normalize 与幂等去重后写入 `trend_items`
- `(user_id, source_type, source_item_key)` 已用于最小 dedupe
- ingest 链路已补齐 `agent_traces`、结构化日志、最小 `tool_invocation_logs`

当前仓库在 Step 4.3 仍明确不做：
- Trend AI 分析
- Trend Inbox 列表 / 动作
- Trend -> Note / Idea 转化
- 复杂聚类、复杂评分、复杂 provider 平台化

说明：
- 当前 trigger 的 `trigger_mode = INGEST`
- 当前单个 source fetch 失败时，整次 trigger 失败
- Step 4.4 才开始写 `analysis_payload` 和 `suggested_action`

### 1.4 Step 4.4 当前落地状态

当前仓库已完成 Step 4.4 的最小 Trend AI 分析闭环：
- 新增 `TrendAnalysisService`
- 新增 `TrendAgent` interface，并提供本地 `StubTrendAgent`
- `POST /api/v1/trends/plans/default/trigger` 在 ingest 后会同步执行最小结构化分析
- 分析结果会写回 `trend_items.summary`
- 分析结果会写回 `trend_items.analysis_payload`
- 分析结果会写回 `trend_items.suggested_action`
- 已落 `ANALYZED` 状态
- analysis 链路已补齐 `agent_traces` 关联、结构化日志、`tool_invocation_logs`

当前仓库在 Step 4.4 仍明确不做：
- 真实外部模型 provider
- 个性化排序
- Trend Inbox controller / page
- Trend -> Note / Idea 转化
- 用户动作事件

说明：
- 当前实现使用本地 deterministic stub，先稳定 analysis contract 与治理链路
- 当前 `trigger_mode` 仍保持 `INGEST`，但 trigger 运行时已包含 analyze 阶段
- 当前 analysis 对单条 trend item 的写库与 `trend.item.analyze` completed log 在同一事务内提交
- 当前 analysis 失败时，失败条目的分析写回会回滚到 `INGESTED`；同一 trigger 中已成功提交的更早条目会保留 `ANALYZED`
- 当前 analysis 失败时，trace 与 trigger 结果会标记失败；已完成 ingest 的 `trend_items` 仍保留为后续重试输入

### 1.5 Step 4.5 当前落地状态

当前仓库已完成 Trend Inbox 的后端查询链路：
- `GET /api/v1/trends/inbox` 已可用
- 默认只返回当前用户的 `ANALYZED` trend items
- 支持可选 `status` 与 `source_type` 过滤
- 查询结果按 `updated_at desc` 返回
- 返回体复用 `TrendInboxItemResponse`
- 查询链路已补齐 controller / service 结构化日志，包含 `trace_id`、`user_id` 与过滤条件

当前仓库已完成 Step 4.5 的前端最小闭环：
- `#/trends` 已接入独立 Trend Inbox 视图，不再是导航壳
- Trend Inbox 页面为单列阅读流，包含最小 `status` / `source_type` 过滤
- 页面支持 loading / empty / error 态
- `IGNORE` 为真实动作，执行后会更新 `trend_items.status = IGNORED` 并刷新列表
- `IGNORE` 失败以卡片级错误提示呈现，不会把整页当成列表加载失败
- `IGNORE`、`SAVE_AS_NOTE`、`PROMOTE_TO_IDEA` 的失败尝试都会保留 `trace_id`，便于从前端错误回查服务端日志
- `SAVE_AS_NOTE` 现在是真实动作，成功后会创建 Note、回写 `converted_note_id`，并跳转到 Notes 详情
- `PROMOTE_TO_IDEA` 现在是真实动作，成功后会创建 Idea、回写 `converted_idea_id`，并跳转到 Ideas 详情
- `Home` 现在是轻量入口页，保留 Capture；Capture 成功后跳转到 Notes 并打开新 Note 详情

说明：
- 当前 inbox 已补齐最小动作链路：`POST /api/v1/trends/{trendItemId}/actions`
- 当前真实支持 `IGNORE`、`SAVE_AS_NOTE` 与 `PROMOTE_TO_IDEA`
- `IGNORE` 链路已写 `agent_traces`、`tool_invocation_logs`、`user_action_events`
- `SAVE_AS_NOTE` 链路已写 `agent_traces`、`tool_invocation_logs`、`user_action_events`
- `PROMOTE_TO_IDEA` 链路已写 `agent_traces`、`tool_invocation_logs`、`user_action_events`
- 成功响应会回传 `trace_id`

### 1.6 Step 4.6 当前落地状态

当前仓库已完成 Step 4.6A / Step 4.6B 的 `Trend -> Note / Idea` 真闭环：
- `SAVE_AS_NOTE` 已接真实转化服务
- 转化成功后会创建 Note，并保留 trend 来源链与分析载荷
- 转化成功后会回写 `trend_items.status = SAVED_AS_NOTE`
- 转化成功后会回写 `converted_note_id`
- 前端会自动跳转到 `Notes` 并打开新建 Note 详情
- `PROMOTE_TO_IDEA` 已接真实转化服务
- 转化成功后会创建 Idea，并保留 trend 来源链与分析载荷
- 转化成功后会回写 `trend_items.status = PROMOTED_TO_IDEA`
- 转化成功后会回写 `converted_idea_id`
- 前端会自动跳转到 `Ideas` 并打开新建 Idea 详情
- 转化链路已补齐 `agent_traces`、`tool_invocation_logs`、`user_action_events`

当前仓库在 Step 4.6 仍明确不做：
- `PROMOTE_TO_IDEA` 自动批量转化
- Trend -> Idea 后自动触发 assess 主链
- Trend 转化后的复杂 proposal 治理

说明：
- 当前 `SAVE_AS_NOTE` 失败会返回 `TREND_NOTE_CONVERSION_FAILED`
- 当前 `PROMOTE_TO_IDEA` 失败会返回 `TREND_IDEA_CONVERSION_FAILED`
- `Trend -> Idea` 后的 assess 仍保持显式用户动作，不在转化步骤里自动触发

### 1.7 Step 4.7 当前落地状态

当前仓库已完成 Step 4.7 的文档与治理收口：
- `docs/codex/Plan.md` 已同步标记 Step 4.7 完成，并把 Phase 4 最小闭环的完成定义收束到当前实现事实
- `docs/codex/Documentation.md` 已对齐当前 Phase 4 落地范围、完成条件与 deferred backlog
- `docs/codex/Prompt.md` 已与 Phase 4 推荐切片顺序保持一致，不再与 Plan / Documentation 漂移

当前仓库在 Step 4.7 不再追加新的业务能力，仅保留后续文档维护和治理对齐的常规工作。

---

## 2. Phase 4 目标说明

Phase 4 的目标不是做一个普通热点流，而是让外部高价值输入进入 Knowledge-to-Action 主线。

Trend 在当前阶段应具备以下能力：
1. 可从受控来源拉取候选
2. 可做最小结构化分析
3. 可进入 Trend Inbox
4. 可由用户决策去留
5. 可转为 Note
6. 可转为 Idea
7. 可为后续偏好学习积累行为事件

---

## 3. Phase 4 领域语义

### 3.1 Trend 的定位

Trend 不是孤立对象，也不是产品唯一卖点。
Trend 是高价值输入增强模块：
- 为系统提供外部候选输入
- 为 Note 生成提供素材
- 为 Idea 孵化提供触发源
- 为后续 Preference 学习提供行为事件

### 3.2 Default Trend Plan 的定位

当前阶段建议提供一个默认内置计划，而不是一开始就建设复杂的用户自定义平台。

默认计划建议为：
- `plan_key = default_ai_engineering_trends`
- 来源：`HN`、`GITHUB`
- 频率：`DAILY`
- 每源抓取上限：5
- 关键词偏置：agent / llm / memory / retrieval / tooling / coding
- `auto_ingest = true`
- `auto_convert = false`

说明：
- 当前阶段允许系统自动入箱
- 当前阶段不允许系统静默自动创建大量 Note / Idea

当前仓库在 Step 4.2 已落地的最小语义：
- plan 配置来源于 `noteops.trend.default-plan`
- 当前 trigger 会真实执行双 source ingest，并在 ingest 后同步执行最小 Trend analysis
- 返回 `trigger_mode = INGEST`

---

## 4. Trend AI 最小分析切片

## 4.1 为什么这是 Phase 4 必须项

如果没有结构化分析，Trend 只是一批外部链接，并不能成为可决策候选。

因此，Phase 4 的最小闭环明确要求提供：
- `TrendAnalysisService`
- `TrendAgent`（最小 provider/stub 均可）
- analysis result 结构化落库
- Trend Inbox 展示分析结果
- 转化动作以分析建议为参考
- trace / log / event 补齐

## 4.2 Trend AI 的管理原则

Trend AI 必须按“受控 Worker Agent”管理，而不是自由模型调用。

AI 负责：
- 结构化理解趋势候选
- 输出简练摘要
- 给出 why-it-matters 解释
- 判断更适合转 Note 还是转 Idea
- 产出 `suggested_action`

AI 不负责：
- 自己抓取网页
- 越权直接创建主业务对象
- 静默大批量转化
- 绕过用户决策直接推进高影响动作

应用层 / 领域层负责：
- 来源拉取
- 输入准备
- provider 调用
- 结果校验
- 入库
- Inbox 展示
- 转化命令执行
- trace / log / event

### 4.3 Step 4.4 当前实现说明

当前最小 runtime 采用以下边界：
- `TrendPlanApplicationService` 负责编排默认 plan trigger、ingest summary、trace 完成态
- `TrendAnalysisService` 负责逐条调用 `TrendAgent`、校验结果、写回 `trend_items`
- `StubTrendAgent` 负责基于 source type、title、score 生成确定性结构化 payload

当前最小写回语义：
- `summary` 使用 analysis summary
- `status` 由 `INGESTED` 升级为 `ANALYZED`
- `analysis_payload` 保存结构化分析载荷
- `suggested_action` 保存当前建议动作
- 同一 trigger 内重复命中的同一 `trend_item_id` 只 analyze 一次

当前最小失败语义：
- source fetch / upsert 失败仍按 Step 4.3 视为整次 trigger 失败
- analysis 失败时，当前 trigger 也视为失败
- `trendAgent` 或 payload contract 校验失败会返回 `TREND_ANALYSIS_FAILED` / `TREND_ANALYSIS_INVALID`
- analysis 写库或 completed tool log 落库失败会返回 `TREND_ANALYSIS_PERSIST_FAILED`
- 失败条目的 analysis 写回会回滚；已落库的 ingest 结果保留，便于后续 retry analyze

---

## 5. Trend analysis 合同

当前阶段建议最小结构如下：

```json
{
  "summary": "string",
  "why_it_matters": "string",
  "topic_tags": ["string"],
  "signal_type": "string",
  "note_worthy": true,
  "idea_worthy": false,
  "suggested_action": "SAVE_AS_NOTE",
  "reasoning_summary": "string"
}
```

说明：
- 当前阶段不要求复杂个性化分数模型
- 当前阶段不要求正式偏好重算
- 当前阶段优先保证结构稳定、可展示、可落库

### 5.1 Step 4.1 已冻结的 `trend_items` 合同

当前 schema 已冻结以下字段：
- `id`
- `user_id`
- `source_type`
- `source_item_key`
- `title`
- `url`
- `summary`
- `normalized_score`
- `analysis_payload`
- `extra_attributes`
- `status`
- `suggested_action`
- `source_published_at`
- `last_ingested_at`
- `converted_note_id`
- `converted_idea_id`
- `created_at`
- `updated_at`

当前约束已冻结：
- `source_type in ('HN', 'GITHUB')`
- `status in ('INGESTED', 'ANALYZED', 'IGNORED', 'SAVED_AS_NOTE', 'PROMOTED_TO_IDEA')`
- `suggested_action in ('IGNORE', 'SAVE_AS_NOTE', 'PROMOTE_TO_IDEA') or null`
- `(user_id, source_type, source_item_key)` 唯一，用于后续 dedupe

---

## 6. API 基线（Phase 4）

### 6.1 Trend Inbox

`GET /api/v1/trends/inbox`

用途：
- 查询当前趋势候选列表
- 提供最小排序与过滤
- 返回 AI 分析后的摘要与建议动作

最小输出语义：
- `trend_item_id`
- `title`
- `source_type`
- `url`
- `summary`
- `normalized_score`
- `suggested_action`

### 6.2 Trend Actions

建议最小新增一个动作接口，例如：

`POST /api/v1/trends/{trend_item_id}/actions`

用途：
- 对 Trend 候选执行用户决策动作

最小输入语义：
- `action = IGNORE | SAVE_AS_NOTE | PROMOTE_TO_IDEA`
- `operator_note`（可选）

最小输出语义：
- `trend_item_id`
- `action_result`
- `converted_note_id`（如有）
- `converted_idea_id`（如有）

说明：
- 也可以按仓库风格拆成多个 endpoint
- 但当前阶段必须保证动作合同真实存在
- 当前 Step 4.1 仅冻结 DTO 形状，不提供 controller 实现

### 6.3 Trend Default Plan Trigger

`POST /api/v1/trends/plans/default/trigger`

用途：
- 显式触发一次默认 Trend plan
- 校验默认 plan 配置
- 解析 `HN` / `GITHUB` source registration
- 拉取候选并执行最小 ingest
- 对本次 ingest / dedupe 命中的条目执行最小结构化分析
- 写 trace / tool log

当前输入语义：
- `user_id`

当前输出语义：
- `plan_key`
- `enabled`
- `resolved_sources`
- `fetch_limit_per_source`
- `schedule`
- `keyword_bias`
- `auto_ingest`
- `auto_convert`
- `trigger_mode = INGEST`
- `fetched_count`
- `inserted_count`
- `deduped_count`
- `source_results`
- `result`

说明：
- 当前 Step 4.4 已执行真实抓取并写入 `trend_items`
- 当前 Step 4.4 会同步写入 `summary`、`analysis_payload`、`suggested_action`
- 当前 Step 4.3 的最小 dedupe 语义是：重复命中仅刷新 `last_ingested_at`（必要时补齐缺失的 `source_published_at`），不覆盖既有 `title`、`url`、`normalized_score`、`extra_attributes`
- 当前 Step 4.3 的最小失败语义是：若 normalize / upsert 中途失败，则本次 ingest 视为整体失败，不保留部分 `trend_items` 写入
- 当前 Step 4.4 的最小 analysis 语义是：按去重后的 `trend_item_id` 写 `trend.item.analyze` tool log，并在 trace 完成态补 `analyzed_count`
- 当前 Step 4.4 的最小 analysis 失败语义是：trigger 返回失败，trace 标记失败；失败条目回滚到 `INGESTED`，但已成功提交的更早分析条目保留
- 当前 Step 4.3 不写 Trend Inbox 用户动作事件

---

## 7. Trend -> Note / Idea 转化

### 7.1 Trend -> Note

适用于信息型候选：
- 新 benchmark
- 高价值文章/讨论
- 热门仓库的核心能力总结

当前阶段要求：
- 生成 Note
- 保留来源链
- 写入简练摘要与必要标签
- 必要时可追加 evidence block

### 7.2 Trend -> Idea

适用于启发型候选：
- 某个设计触发了产品想法
- 某个趋势暴露了需求空白
- 某个项目能被抽象成新功能或实验方向

当前阶段要求：
- 生成 Idea
- 保留来源链
- 允许复用既有 Idea assess 流程

说明：
- Trend 阶段不重复实现 Idea 评估平台
- Trend -> Idea 的后续评估应复用既有 `ideas/{id}/assess`

---

## 8. 可观测性与治理要求（Phase 4）

Phase 4 新增核心链路必须补齐：

1. 结构化日志
2. `agent_traces`
3. 必要的 `tool_invocation_logs`
4. 至少一个相关 `user_action_event`

最小日志点应覆盖：
- Trend plan 触发入口
- source 拉取开始 / 成功 / 失败
- Trend analysis 调用开始 / 成功 / 失败
- trend item 入库
- Trend Inbox 用户动作
- Trend -> Note 转化
- Trend -> Idea 转化

日志至少应包含：
- `trace_id`
- `user_id`
- `trend_item_id`（如适用）
- `source_type`
- `action`
- `result`
- `duration_ms`（如适用）
- `error_code` / `error_message`（失败时）

同时建议至少记录以下 `user_action_events`：
- `TREND_IGNORED`
- `TREND_SAVED_AS_NOTE`
- `TREND_PROMOTED_TO_IDEA`

这些事件将作为未来偏好学习和排序评估输入。

当前 Trend 最小闭环已落地的最小 `tool_invocation_logs` 包括：
- `trend.source_registry.resolve`
- `trend.source.fetch`
- `trend.item.upsert`
- `trend.item.analyze`
- `trend.item.action`
- `trend.note.convert`
- `trend.idea.convert`

---

## 9. Web 交付基线（Phase 4）

当前阶段前端最小目标：

1. Trend Inbox List
2. 候选摘要展示
3. suggested_action 展示
4. IGNORE 按钮
5. SAVE_AS_NOTE 按钮
6. PROMOTE_TO_IDEA 按钮
7. 加载 / 空 / 错误态

当前阶段前端不优先：
- 复杂视觉重构
- 可拖拽多列布局
- 个性化筛选平台
- 高级趋势图表

---

## 10. 当前 deferred backlog

以下能力当前阶段可以明确后置，但不能丢失：

1. 用户自定义 Trend Plan
    - 原因：当前先保证默认计划与最小闭环
    - 预计补回：Phase 4 后段或 Phase 5
    - 当前限制：暂不支持按用户偏好调整 source、schedule 或 keyword bias

2. 多来源正式 connector 平台
    - 原因：当前先保证 HN / GitHub 默认主线
    - 预计补回：Phase 4 后段
    - 当前限制：除 HN / GitHub 外的 provider 仍需显式接入，不能直接平台化扩展

3. Trend 个性化排序
    - 原因：Preference 正式闭环尚未开始
    - 预计补回：Phase 5
    - 当前限制：Trend Inbox 只按当前阶段的最小规则排序，尚不依赖个体画像

4. Trend 去重 / 聚类高级优化
    - 原因：当前先保证可运行，不做复杂质量工程
    - 预计补回：Phase 4 后段
    - 当前限制：只保留最小 dedupe 语义，尚未做高级聚类、事件合并或主题漂移分析

5. Trend 批量转化与自动化规则
    - 原因：当前坚持建议优先，不做静默高影响动作
    - 预计补回：Phase 4 后段或 Phase 5
    - 当前限制：所有高影响转化仍需用户显式动作，不提供静默批量转化

6. Trend 真实模型 provider / prompt 治理
    - 原因：当前先用本地 stub 稳定 analysis contract、trace 与日志链路
    - 预计补回：Phase 4 后段
    - 当前限制：分析结果仍由本地 deterministic stub 产生，尚未接入真实外部模型 provider

---

## 11. 当前完成定义

仅当以下条件同时满足，才可标记为“Phase 4 已完成最小闭环”：

1. 有默认 Trend Plan
2. 至少支持 HN / GitHub 两个默认来源
3. 能真实拉取并入库 Trend 候选
4. 能对候选做结构化分析
5. 有 Trend Inbox
6. 可执行 ignore / save as note / promote to idea
7. Trend -> Note / Idea 真实可用
8. trace / log / event / docs 已同步

若只完成表、静态列表、假数据或手工写库，不可标记为已完成最小闭环。
