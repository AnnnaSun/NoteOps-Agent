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
- 当前 trigger 会真实执行双 source ingest
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
- 当前 Step 4.3 已执行真实抓取并写入 `trend_items`
- 当前 Step 4.3 的最小 dedupe 语义是：重复命中仅刷新 `last_ingested_at`（必要时补齐缺失的 `source_published_at`），不覆盖既有 `title`、`url`、`normalized_score`、`extra_attributes`
- 当前 Step 4.3 的最小失败语义是：若 normalize / upsert 中途失败，则本次 ingest 视为整体失败，不保留部分 `trend_items` 写入
- 当前 Step 4.3 不写 `analysis_payload`
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

2. 多来源正式 connector 平台
    - 原因：当前先保证 HN / GitHub 默认主线
    - 预计补回：Phase 4 后段

3. Trend 个性化排序
    - 原因：Preference 正式闭环尚未开始
    - 预计补回：Phase 5

4. Trend 去重 / 聚类高级优化
    - 原因：当前先保证可运行，不做复杂质量工程
    - 预计补回：Phase 4 后段

5. Trend 批量转化与自动化规则
    - 原因：当前坚持建议优先，不做静默高影响动作
    - 预计补回：Phase 5 以后

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
