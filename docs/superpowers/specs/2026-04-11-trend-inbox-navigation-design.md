# Trend Inbox Navigation Design

## Context

当前仓库已完成 Phase 4 Step 4.4：Trend ingest 后可同步执行最小结构化分析，并把 `summary`、`analysis_payload`、`suggested_action` 写回 `trend_items`。

当前未完成的下一个切片是 Step 4.5：Trend Inbox。现有前端主页面把 `Notes`、`Ideas`、`Workspace` 混排在同一个重型单页中，不适合继续承接 Trend Inbox 的独立决策场景。

本设计的目标是在不越过 Step 4.6 的前提下，完成：

1. 轻首页 `Home`
2. 独立内容页面：`Notes`、`Ideas`、`Workspace`、`Trend Inbox`
3. `Capture` 从重型单页迁移到 `Home`
4. `GET /api/v1/trends/inbox` 最小真实接口
5. `Trend Inbox` 最小可浏览、可执行 `IGNORE` 的决策页面

## Goals

### Product goals

1. 用户进入应用后，先看到轻量入口页，而不是重型混合工作台。
2. 每个主页面主体只展示一种内容域。
3. `Trend Inbox` 成为独立的候选决策页面，而不是附着在现有工作台中的一个区块。
4. `Capture` 仍然是主入口能力，但创建成功后的详情进入内容库页面承接。

### Phase goals

1. 完成 Step 4.5 的最小闭环，不提前实现 Step 4.6 的真实转化。
2. 保持 Trend 相关 trace / log / user action event 的可追溯性。
3. 尽量复用现有 DTO、查询模型和前端数据流，不做无关重构。

## Non-Goals

1. 本设计不实现 `SAVE_AS_NOTE` 的真实转化闭环。
2. 本设计不实现 `PROMOTE_TO_IDEA` 的真实转化闭环。
3. 本设计不引入复杂筛选、批量操作、搜索、聚类或趋势可视化。
4. 本设计不重构 Note / Idea / Workspace 的内部业务逻辑，只做页面边界与展示拆分。
5. 本设计不引入多层路由体系或复杂 breadcrumbs。

## Visual Direction

### Chosen direction

本次设计采用 `Calm Analytical` 方向。

### Visual principles

1. 整体视觉应冷静、克制、偏编辑感，不走高饱和产品营销风格。
2. 页面保留较大留白，降低首屏信息密度。
3. 以中性灰黑和偏暖浅底为主，辅以低饱和 teal 作为状态与边界强调。
4. 强调色只用于真正的主动作，不大面积使用。
5. 交互动效保持轻量，优先稳定与可读性。

### Visual system

1. `Home` 是轻入口页，不展示内容列表。
2. `Trend Inbox` 使用单列阅读流卡片，不做左右分栏详情。
3. 卡片保留清晰层级：
   - 第一层：标题、来源、score、建议动作
   - 第二层：summary
   - 第三层：why-it-matters、topic tags
   - 第四层：动作按钮

## Information Architecture

### Top-level pages

应用在本设计下采用 5 个主页面：

1. `Home`
2. `Notes`
3. `Ideas`
4. `Workspace`
5. `Trend Inbox`

### Page roles

#### Home

`Home` 是入口页，不再承担重型工作台职责。它只负责：

1. 展示极轻摘要计数
2. 展示四个内容域入口
3. 提供 `Capture` 启动区

`Home` 不展示具体 Note、Idea、Task、Trend 列表。

#### Notes

`Notes` 页面只展示 Note 内容域，包括现有 Note 列表与 Note 详情行为。

#### Ideas

`Ideas` 页面只展示 Idea 内容域，包括 Idea 列表、详情和现有 assess / generate-task 行为。

#### Workspace

`Workspace` 页面只展示 today / upcoming 聚合工作流，不再与 Note、Idea、Trend 同屏混排。

#### Trend Inbox

`Trend Inbox` 页面只展示趋势候选与相关决策动作，是外部输入进入系统的独立工作面。

### Global navigation

所有页面共享一层极简全局导航，允许用户在五个主页面之间切换。

导航要求：

1. 结构扁平
2. 不使用多层导航
3. 不增加 breadcrumbs
4. 当前页面有明确选中态
5. 页面切换失败不应导致整个应用不可用

## Home Design

### Home structure

`Home` 页面包含三个区块：

1. 顶部标题区
2. 极轻摘要区
3. 入口与 `Capture` 区

### Summary section

摘要区只展示计数，不展示列表。建议最小计数包括：

1. `Notes`
2. `Ideas`
3. `Today Items`
4. `Trends`

### Entry section

入口区提供四个主入口：

1. `Notes`
2. `Ideas`
3. `Workspace`
4. `Trend Inbox`

入口卡片可展示简短描述与数量，但不显示具体条目预览。

### Capture section

`Capture` 从原有重型页面迁移到 `Home`。该区负责：

1. 新建 capture
2. 展示最小输入表单
3. 提交成功后跳转

### Capture success behavior

当 capture 创建成功后，页面行为为：

1. 自动跳转到 `Notes`
2. 自动选中新创建的 Note
3. 自动展示该 Note 的详情面板

`Home` 不承载 capture 结果详情，以避免重新变重。

## Trend Inbox Design

### Page purpose

`Trend Inbox` 的职责是把外部趋势候选变成可决策对象，而不是做信息聚合首页。

### Page structure

页面由三部分构成：

1. 页头
2. 最小工具条
3. 单列卡片流

### Header

页头展示：

1. 页面名称 `Trend Inbox`
2. 简短说明文案
3. 当前候选总数或筛选后的数量摘要

### Toolbar

工具条保持最小范围，只包括：

1. `status` 过滤
2. `source_type` 过滤
3. 排序说明

当前阶段默认：

1. `status = ANALYZED`
2. `source_type = ALL`
3. `sort = updated_at desc`

### Card layout

每个 Trend 卡片采用单列阅读流，信息层级如下：

#### Primary layer

1. `title`
2. `source_type`
3. `normalized_score`
4. `suggested_action`
5. `status`

#### Secondary layer

1. `summary`

#### Tertiary layer

1. `why_it_matters`
2. `topic_tags`

#### Action layer

展示三个动作按钮：

1. `Ignore`
2. `Save as Note`
3. `Promote to Idea`

### Action semantics for Step 4.5

本步骤只允许一个真实动作闭环：

1. `IGNORE` 为真实可执行动作
2. `SAVE_AS_NOTE` 为 disabled 占位入口
3. `PROMOTE_TO_IDEA` 为 disabled 占位入口

disabled 入口必须明确表达“后续步骤实现”，避免用户误解为已闭环。

## Backend Design

### New API

新增最小接口：

`GET /api/v1/trends/inbox`

### Query parameters

最小支持：

1. `user_id`
2. 可选 `status`
3. 可选 `source_type`

### Default behavior

默认行为：

1. 返回当前用户的 Trend Inbox 列表
2. 默认优先返回 `ANALYZED` 条目
3. 按 `updated_at desc` 排序

### Response contract

优先复用现有 `TrendInboxItemResponse`。首屏页面最小依赖字段：

1. `trend_item_id`
2. `source_type`
3. `title`
4. `summary`
5. `normalized_score`
6. `status`
7. `suggested_action`
8. `analysis_payload`
9. `updated_at`

### Application layering

后端遵循现有分层：

1. Controller 负责参数解析与 response envelope
2. Query / Application Service 负责列表查询编排
3. Repository 负责最小筛选与排序查询

本步骤应优先用查询服务而不是把 repository 直接暴露给 controller。

## Frontend Design

### Navigation model

前端采用最小多页面结构，而不是继续维持单个重型内容面板。

这一步的目标不是引入复杂路由系统，而是建立清晰的主视图切换。

### Frontend page responsibilities

1. `Home` 负责入口、摘要、capture
2. `Notes` 负责现有 note 列表和详情
3. `Ideas` 负责现有 idea 列表和详情
4. `Workspace` 负责现有 today / upcoming
5. `Trend Inbox` 负责加载 `GET /api/v1/trends/inbox` 并展示单列卡片流

### Frontend state guidance

状态组织遵循最小原则：

1. 主视图状态集中在顶层
2. 由主视图派生的显示数据尽量不重复存储
3. 每个页面的加载态、空态、错误态局部处理
4. `Trend Inbox` 失败不能拖垮其他页面

## Logging And Traceability

### Inbox query logging

`GET /api/v1/trends/inbox` 至少补充：

1. request received log
2. result summary log
3. fail log

字段至少包含：

1. `trace_id`
2. `user_id`
3. `path`
4. `action`
5. `result`
6. `status`
7. `source_type`（如有）
8. `error_code` / `error_message`（失败时）

### Ignore action logging

`IGNORE` 动作至少补充：

1. action start
2. action success
3. action fail
4. `tool_invocation_logs`
5. `user_action_events`

关键字段至少包含：

1. `trace_id`
2. `user_id`
3. `trend_item_id`
4. `action=IGNORE`
5. `result`

## Error Handling

### Inbox page errors

1. 列表查询失败只影响 `Trend Inbox` 页面自身
2. 页面要显示局部错误态
3. 用户仍可切换到其他主页面

### Ignore action errors

1. `IGNORE` 失败只影响当前卡片
2. 局部提示失败信息
3. 不刷新整页
4. 不清空现有列表

### Capture errors

1. `Capture` 提交失败时，错误留在 `Home`
2. 不跳转到 `Notes`
3. 保留用户输入，便于修正后重试

## Testing Strategy

### Backend tests

1. `Trend Inbox` controller test
2. query/application service test
3. repository query test
4. `IGNORE` action test

### Frontend tests

1. 主页面切换最小验证
2. `Trend Inbox` 加载态 / 空态 / 错误态
3. `IGNORE` 成功后的局部刷新
4. `Capture` 成功后跳转到 `Notes` 并自动打开新 Note 详情

### Manual verification

至少手工验证：

1. 从 `Home` 进入 `Trend Inbox`
2. `Trend Inbox` 只显示趋势内容
3. `IGNORE` 后状态变化可见
4. `Capture` 成功后进入 `Notes` 详情

## Deferred To Step 4.6

以下能力明确延后到 Step 4.6：

1. `SAVE_AS_NOTE` 真实转化
2. `PROMOTE_TO_IDEA` 真实转化
3. Trend 与 Note / Idea 的来源链落库闭环
4. 转化后的跨页面跳转闭环
5. 转化相关 `user_action_events` 的完整链路

## Recommended Implementation Slice

实现应按以下顺序推进：

1. 后端 `GET /api/v1/trends/inbox`
2. Trend Inbox 列表测试与查询测试
3. 前端主视图拆分与轻首页
4. `Capture` 迁移到 `Home`
5. `Trend Inbox` 页面接真实接口
6. `IGNORE` 动作闭环
7. 文档同步

## Acceptance Criteria

满足以下条件时，本设计对应的 Step 4.5 可视为完成：

1. 应用具备轻首页与五个独立主页面
2. 每个页面主体只展示一种内容域
3. `Home` 提供 `Capture` 启动能力
4. `Capture` 成功后跳转到 `Notes` 并自动打开新 Note 详情
5. `GET /api/v1/trends/inbox` 可返回真实数据
6. `Trend Inbox` 可展示 AI 分析结果
7. `IGNORE` 真实可执行且具备 trace / log / event
8. `SAVE_AS_NOTE` / `PROMOTE_TO_IDEA` 未被误记为已完成
