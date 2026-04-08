# Documentation
# docs/codex/Documentation.md

## 1. 当前阶段状态

当前仓库开发基线仍处于：

# Phase 3：Idea Lifecycle / Idea Workspace

但当前真实完成度已推进到：

- `Step 3.4：Idea -> Task 派生`

本次已真实落地的范围：

- `ideas` 表与索引
- Idea 来源枚举：`FROM_NOTE` / `INDEPENDENT`
- Idea 状态枚举：`CAPTURED` / `ASSESSED` / `PLANNED` / `IN_PROGRESS` / `ARCHIVED`
- `assessment_result` 结构化合同
- `IdeaRepository` / `JdbcIdeaRepository`
- `CreateIdeaRequest` / `IdeaResponse` DTO 冻结
- `POST /api/v1/ideas`
- `IdeaApplicationService` / `IdeaController`
- Idea create 的最小 trace / user_action_event / structured logging
- `AssessIdeaRequest`
- `POST /api/v1/ideas/{id}/assess`
- `IdeaAssessmentService`
- `IdeaAgent` / `StubIdeaAgent`
- Idea assess 的最小 trace / tool_invocation_logs / user_action_event / structured logging
- `GenerateIdeaTaskRequest`
- `IdeaTaskGenerationResponse`
- `POST /api/v1/ideas/{id}/generate-task`
- `IdeaTaskGenerationService`
- Idea -> Task 的最小 trace / user_action_event / structured logging

当前仍**未实现**：

- `GET /api/v1/ideas`
- `GET /api/v1/ideas/{id}`
- Idea Web 工作台

---

## 2. Step 3.1 已冻结的领域语义

### 2.1 Idea 的定位

Idea 是独立实体，但仍保持 Note-first 约束：

- 可以独立存在
- 可以绑定来源 Note
- 后续可以进入 assess / task / workspace
- 当前已支持最小 create 命令，assess / task / workspace 仍待后续切片

### 2.2 Idea 来源

当前仅冻结两种来源：

- `FROM_NOTE`
- `INDEPENDENT`

约束固定为：

- `FROM_NOTE` 时 `source_note_id` 必填
- `INDEPENDENT` 时 `source_note_id` 必须为空

### 2.3 Idea 生命周期

当前阶段已冻结以下状态值：

- `CAPTURED`
- `ASSESSED`
- `PLANNED`
- `IN_PROGRESS`
- `ARCHIVED`

当前只实现 create 进入 `CAPTURED`，尚未实现 assess / promote / archive 等后续状态推进命令。

---

## 3. 当前真实 Schema / Contract

### 3.1 `ideas` 表

当前真实字段：

- `id`
- `user_id`
- `source_mode`
- `source_note_id`
- `title`
- `raw_description`
- `status`
- `assessment_result`
- `created_at`
- `updated_at`

当前真实约束：

- `source_mode in ('FROM_NOTE', 'INDEPENDENT')`
- `status in ('CAPTURED', 'ASSESSED', 'PLANNED', 'IN_PROGRESS', 'ARCHIVED')`
- `source_note_id` 外键指向 `notes(id)`
- `FROM_NOTE` / `INDEPENDENT` 与 `source_note_id` 的交叉校验

当前真实索引：

- `(user_id, updated_at desc)`
- `(user_id, status, updated_at desc)`
- `(user_id, source_note_id, updated_at desc)`

### 3.2 `assessment_result` 合同

当前已冻结的最小结构：

```json
{
  "problem_statement": "string",
  "target_user": "string",
  "core_hypothesis": "string",
  "mvp_validation_path": [
    "string"
  ],
  "next_actions": [
    "string"
  ],
  "risks": [
    "string"
  ],
  "reasoning_summary": "string"
}
```

当前语义：

- schema 层默认值仍为 `{}`，用于兜底数据库写入
- 当前 create 路径会显式写入 `IdeaAssessmentResult.empty()` 对应的空 assessment 结构，读取后统一表现为“尚未 assess”
- 本切片只保证结构稳定与可映射，不代表 assess 接口已存在

### 3.3 DTO / API 基线

当前已冻结 DTO：

- `AssessIdeaRequest`
- `CreateIdeaRequest`
- `IdeaResponse`

字段命名统一使用 snake_case：

- `source_mode`
- `source_note_id`
- `raw_description`
- `assessment_result`
- `created_at`
- `updated_at`

当前说明：

- DTO 已用于当前 create / assess / task-generation 路由
- 当前真实已注册：
  - `POST /api/v1/ideas`
  - `POST /api/v1/ideas/{id}/assess`
  - `POST /api/v1/ideas/{id}/generate-task`
- 当前尚未注册：
  - `GET /api/v1/ideas`
  - `GET /api/v1/ideas/{id}`

### 3.4 Create Idea 当前语义

`POST /api/v1/ideas`

当前请求字段：

- `user_id`
- `source_mode`
- `source_note_id`
- `title`
- `raw_description`

当前真实行为：

- `source_mode=INDEPENDENT` 时创建独立 Idea
- `source_mode=FROM_NOTE` 时要求 `source_note_id` 存在且属于当前 `user_id`
- 初始状态固定写入 `CAPTURED`
- `assessment_result` 初始写入空 assessment 结构，并在 repository 侧统一映射为 `IdeaAssessmentResult.empty()`
- 创建成功后写入：
  - `agent_traces`
  - `user_action_events`
  - 结构化日志

当前事件语义：

- 独立创建：`IDEA_CREATED`
- 从 Note 派生：`IDEA_DERIVED_FROM_NOTE`

### 3.5 Assess Idea 当前语义

`POST /api/v1/ideas/{id}/assess`

当前请求字段：

- `user_id`

当前真实行为：

- 仅允许 `CAPTURED` 状态进入 assess
- 会读取当前 Idea 记录；如存在 `source_note_id`，会补充 source note 的标题、摘要、关键点进入 assessment context
- 当前通过 `StubIdeaAgent` 生成确定性结构化 assessment，不依赖真实外部 provider
- assessment 成功后：
  - `ideas.assessment_result` 被更新
  - `status` 从 `CAPTURED` 推进到 `ASSESSED`
  - 写入 `agent_traces`
  - 写入 `tool_invocation_logs`
  - 写入 `user_action_events`
  - 写入结构化日志

当前事件语义：

- assess 成功：`IDEA_ASSESSED`
- assess 失败：`IDEA_ASSESS_FAILED`

### 3.6 Idea -> Task 当前语义

`POST /api/v1/ideas/{id}/generate-task`

当前请求字段：

- `user_id`

当前真实行为：

- 仅允许 `ASSESSED` 状态进入 task generation
- 要求 `assessment_result.next_actions` 至少包含 1 条动作
- 会为每条 `next_action` 创建一个 `SYSTEM` task
- 当前会对 `next_actions` 做最小去重，避免同一 assessment 的重复动作生成重复 task
- 生成 task 时固定写入：
  - `task_source=SYSTEM`
  - `task_type=IDEA_NEXT_ACTION`
  - `status=TODO`
  - `related_entity_type=IDEA`
  - `related_entity_id=<idea_id>`
- 若 Idea 来自 Note，则生成 task 时会复用 `source_note_id` 作为 `note_id`
- 当前 `due_at` 策略为：
  - 第一个 task 使用当前生成时间，默认进入 Today
  - 后续 task 按天顺延，默认可进入 Upcoming
- 当前生成 task 后会把 Idea 状态从 `ASSESSED` 推进到 `PLANNED`
- 生成后的 task 可按当前默认调度进入 Today / Upcoming 聚合视图
- 当前通过 compare-and-set 状态推进避免同一个 `ASSESSED` Idea 被并发重复生成任务
- 当前会写入：
  - `agent_traces`
  - `user_action_events`
  - 结构化日志

当前事件语义：

- task generation 成功：`IDEA_TASKS_GENERATED`
- task generation 失败：`IDEA_TASK_GENERATION_FAILED`

---

## 4. 当前未完成边界

以下能力仍属于后续 Step，不应误判为已落地现实：

1. Step 3.5：Idea Web 工作台
2. Step 3.6：Phase 3 文档与治理收口

特别说明：

- 当前 `IdeaRepository` 只提供 `create(...)`、`findByIdAndUserId(...)`、`updateAssessment(...)` 与 `updateStatus(...)`
- 当前没有 `GET /api/v1/ideas` / `GET /api/v1/ideas/{id}`
- 当前 assess 仍是 `StubIdeaAgent`，不代表真实 provider 已接入
- 当前 task generation 仍不包含复杂批量拆解、优先级学习或计划时间推断

---

## 5. 当前 deferred backlog

以下能力当前阶段可以明确后置，但不能丢失：

1. Idea assess / task 的完整治理链路
  - 原因：当前只完成最小 task generation，未补 proposal 治理和更复杂的计划决策
  - 预计补回：Phase 3 后段或 Phase 4

2. 多 provider / model routing
  - 原因：当前 assess 先使用 `StubIdeaAgent` 保证合同与主链路稳定
  - 预计补回：Phase 3 后段或 Phase 4

3. assessment scoring / 商业评分
  - 原因：当前先保证结构化评估，不做伪精细化
  - 预计补回：Phase 4 以后

4. Trend 与 Idea 联动正式流
  - 原因：Trend 正式阶段尚未开始
  - 预计补回：Phase 4

5. `user_preference_profiles` 对 Idea 排序的真实影响
  - 原因：Preference 正式闭环尚未开始
  - 预计补回：Phase 5

6. Idea 高级工作台（Kanban / pipeline / bulk actions）
  - 原因：先做最小可用，不做界面平台化
  - 预计补回：Phase 3 后段或 Phase 4

---

## 6. 当前完成定义

当前可以标记为：

- `Step 3.4 已完成最小闭环`

当前**不能**标记为：

- `Phase 3 已完成最小闭环`

因为以下条件仍未满足：

1. Web 能展示 Idea 主路径
2. Idea List / Detail 尚未可用
3. 复杂计划推进和后续状态交互尚未实现
