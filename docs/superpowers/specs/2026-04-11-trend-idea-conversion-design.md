# Trend Idea Conversion Design

## Context

当前仓库已完成 Phase 4 的 Step 4.6A：`SAVE_AS_NOTE` 已经接成真实闭环，能够把 `ANALYZED` Trend 候选转为 Note，并保留来源链、trace、tool log 和 user action event。

当前下一步是 Step 4.6B：`PROMOTE_TO_IDEA`。本步的目标不是把 Trend 变成一个新的独立系统，而是把已分析的 Trend 候选显式提升到现有 Idea 主链，随后由用户手动进入现有 `Idea assess` 流程。

本设计要解决的问题是：

1. `PROMOTE_TO_IDEA` 需要有真实转化闭环
2. 转化后的 Idea 必须可从现有 `IdeaController` 读取和操作
3. Trend 来源链、trace、tool log、user action event 必须完整保留
4. 不能把 `Idea assess` 主链自动化或吞并进 Trend 转化链

## Goals

### Product goals

1. 用户在 `Trend Inbox` 中点击 `Promote to Idea` 后，可以得到一条真实的 Idea 记录。
2. 新 Idea 会进入现有 Idea 页面，并可继续手动 assess。
3. Trend 转 Idea 的来源链可回查，后续可追踪这条 Idea 是从哪条 Trend 候选来的。
4. 该动作与 `SAVE_AS_NOTE` 保持同级，都是受控的、可追踪的用户决策动作。

### Phase goals

1. 只完成 Step 4.6B 的最小闭环，不额外实现自动 assess。
2. 保持 `Trend`、`Idea`、`agent_traces`、`tool_invocation_logs`、`user_action_events` 的语义一致。
3. 尽量复用现有 `IdeaRepository`、`IdeaController`、`IdeaQueryService` 和前端 Ideas 详情页。

## Non-Goals

1. 本设计不实现自动 assess 新 Idea。
2. 本设计不实现 Trend 批量转 Idea。
3. 本设计不实现更复杂的 Idea 生成质量评估、去重或聚类。
4. 本设计不改造现有 Idea assess 的状态机。
5. 本设计不引入 Trend 专属的 Idea 浏览页。

## Chosen Approach

### Decision

采用“独立转换服务 + 现有 Idea 主链承接”的方案。

### Why this approach

1. Trend 转 Idea 本质上是来源转换，不是评估编排。
2. 现有 Idea assess 已经是一条独立主链，应保持可见、可控、可手动触发。
3. 新 Idea 的创建逻辑可以复用现有 Idea repository / detail / controller 套件，不需要再发明一套新的对象模型。
4. 用户可以明确看到：Trend 只是进入 Idea 主链的入口，后续处理仍由 Idea 页面承担。

## Data Model

### Idea source extension

现有 `IdeaSourceMode` 只有：

1. `FROM_NOTE`
2. `MANUAL`

本设计新增：

1. `FROM_TREND`

### Idea provenance

为了保留来源链，Idea 需要新增来源字段：

1. `source_trend_item_id`

该字段用于记录这条 Idea 来自哪一个 Trend 候选。

### Trend back-reference

Trend 侧已经冻结了：

1. `converted_idea_id`

本设计将继续使用这个字段，将 Trend 与新创建的 Idea 双向关联起来。

### Idea status

新创建的 Trend-derived Idea 进入现有 Idea 生命周期的起点：

1. `status = CAPTURED`

本设计不在转化时自动推进到 `ASSESSED`。

## Conversion Flow

### Happy path

1. 用户在 `Trend Inbox` 点击 `PROMOTE_TO_IDEA`。
2. `TrendActionApplicationService` 校验当前 Trend 条目可操作。
3. 交给新的 `TrendIdeaConversionService`。
4. 转换服务读取 Trend 条目的 `title`、`summary`、`analysis_payload`、`normalized_score`、`source_type`、`source_item_key`、`url` 和 `trend_item_id`。
5. 创建一条 Idea，`source_mode = FROM_TREND`，`source_trend_item_id = trend_item_id`。
6. Trend 条目回写：
   - `status = PROMOTED_TO_IDEA`
   - `converted_idea_id = <new idea id>`
7. 写入 `agent_traces`、`tool_invocation_logs`、`user_action_events`。
8. 前端跳到 `Ideas` 页面，并打开新建 Idea 详情。
9. 用户后续可在 Ideas 页面手动点 `assess`。

### Mapping rules

建议的最小映射规则：

1. `Idea.title` 优先使用 Trend title。
2. `Idea.raw_description` 由 Trend summary 与 analysis summary 组合而成。
3. `Idea.source_trend_item_id` 保存来源 Trend id。
4. `Idea.assessment_result` 先使用空值或空结构，不在转换时生成 assessment。
5. `Idea.status` 先落 `CAPTURED`。

### Trace and audit

转换流程必须保证：

1. 单条 trace 可串起 Trend action 请求、Idea 创建、Trend 回写、事件写入。
2. 失败时 trace 也应落库，便于回查。
3. `PROMOTE_TO_IDEA` 失败时返回受控错误码，而不是静默降级。

## API Behavior

### Existing endpoint

继续使用现有接口：

`POST /api/v1/trends/{trend_item_id}/actions`

### Request

请求仍使用现有 `TrendActionRequest`：

1. `user_id`
2. `action`
3. `operator_note`

当 `action = PROMOTE_TO_IDEA` 时进入本设计的真实转换路径。

### Response

响应继续复用现有 `TrendActionResponse`，成功时补充：

1. `converted_idea_id`
2. `trace_id`
3. `trend_item_id`
4. `action_result = PROMOTED_TO_IDEA`

### Error semantics

本设计建议的受控错误码：

1. `TREND_IDEA_CONVERSION_FAILED`

以下情况应返回受控错误：

1. Trend 条目不存在
2. Trend 条目不属于当前用户
3. Trend 条目不是 `ANALYZED`
4. Idea 创建或回写失败
5. trace / log / event 落库失败

## Logging and Events

### Structured logs

必须覆盖：

1. `trend_action_start`
2. `trend_idea_convert_start`
3. `trend_idea_convert_success`
4. `trend_idea_convert_fail`

每条日志至少包含：

1. `trace_id`
2. `user_id`
3. `trend_item_id`
4. `idea_id`（成功后）
5. `action`
6. `result`
7. `duration_ms`
8. `error_code` / `error_message`（失败时）

### Tool invocation logs

建议使用工具名：

1. `trend.idea.convert`

成功与失败都要写入。

### User action events

建议新增事件：

1. `TREND_PROMOTED_TO_IDEA`

事件 payload 至少包含：

1. `trend_item_id`
2. `idea_id`
3. `action`
4. `status`
5. `source_type`
6. `source_item_key`
7. `operator_note`
8. `suggested_action`

## Frontend Behavior

### Trend Inbox

`Promote to Idea` 成功后：

1. 跳转到 `Ideas`
2. 自动打开新 Idea 详情
3. 刷新 Ideas 列表

### Ideas page

新 Idea 应该和现有 Ideas 数据一致，允许用户：

1. 查看详情
2. 手动 assess
3. 生成 task

本设计不增加新的 “Trend-derived Idea” 专用 UI 区块。

## Testing Strategy

### Backend tests

1. controller test：`PROMOTE_TO_IDEA` 成功、失败、非法状态
2. service test：Idea 创建成功、Trend 回写成功、trace / event / log 成功
3. repository / integration test：新 Idea 能通过现有 `IdeaController` 读取详情
4. regression test：`SAVE_AS_NOTE` 和 `IGNORE` 的行为不回退

### Frontend tests

1. `Promote to Idea` 成功后跳转到 `Ideas`
2. 新 Idea 详情可被选中
3. `Trend Inbox` 仍然只做单列阅读流，不引入新布局复杂度

## Deferred Backlog

本设计明确后置的能力：

1. 自动在 Trend 转 Idea 后触发 assess
2. Trend-driven Idea 批量转化
3. 更复杂的 trend-to-idea 质量评分
4. Trend / Idea 跨对象智能去重
5. Trend 来源对 Idea assess 的个性化影响

这些能力不属于 Step 4.6B 的最小闭环。

## Acceptance Criteria

只有同时满足以下条件，才算 Step 4.6B 达成最小闭环：

1. Trend Inbox 中可点击 `Promote to Idea`
2. 成功后创建真实 Idea
3. Idea 可以在现有 Ideas 页面读取与查看
4. Trend 条目回写 `converted_idea_id`
5. Trend 条目状态推进到 `PROMOTED_TO_IDEA`
6. trace / log / event 全链路可回查
7. 不自动 assess，新 Idea 仍停留在 `CAPTURED`

