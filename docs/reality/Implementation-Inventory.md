# Implementation Inventory

更新时间：2026-04-09

本清单基于当前工作区现实，而不是 `git HEAD`。仓库当前存在未提交改动，因此本文档描述的是“此刻工作树里的实现现实”。本次盘点的直接依据主要包括：

- `AGENTS.md`
- `docs/codex/Prompt.md`
- `docs/codex/Plan.md`
- `docs/codex/Implement.md`
- `docs/codex/Documentation.md`
- `docs/history/Documentation.md`
- `server/src/main/resources/db/migration/V1__create_phase1_core_tables.sql`
- `server/src/main/resources/db/migration/V2__create_ideas_table.sql`
- `server/src/main/resources/db/migration/V3__rename_idea_source_mode_to_manual.sql`
- `server/src/main/java/**`
- `web/src/**`
- `server/src/test/java/**`

## 1. 仓库现实概览

- 后端是 Spring Boot 3.5 + JDBC + Flyway + PostgreSQL。
- 前端是 React 18 + Vite 单页工作台，不是多页面路由应用。
- 持久层没有 ORM entity 类；真实模型主要表现为：
  - SQL migration 中的表结构
  - repository 中的 JDBC 映射
  - service / dto 里的 `record`
- 当前代码里已经存在最小 Idea 主线：
  - `V2__create_ideas_table.sql`
  - `V3__rename_idea_source_mode_to_manual.sql`
  - `controller/idea`
  - `service/idea`
  - `repository/idea`
  - `dto/idea`
  - Web 单页内的 Idea Workspace 面板
- 当前 Idea 来源现实已统一为：
  - `FROM_NOTE`
  - `MANUAL`
- 当前真实主链路是：Capture、Note Query、Search、Search Governance、Review、Task、Workspace、ChangeProposal、Trace/Event，以及最小 Idea create / assess / task / query / workspace。

## 2. 领域模型现实清单

## 2.1 `notes`

表：`notes`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | Note 主键 |
| `user_id` | `uuid not null` | 用户边界 |
| `note_type` | `varchar(64)` | 当前代码写死为 `CAPTURE_NOTE` |
| `status` | `varchar(32)` | 当前代码写死为 `ACTIVE`，无枚举约束 |
| `title` | `varchar(255)` | 当前标题 |
| `current_summary` | `text` | 当前解释层摘要 |
| `current_key_points` | `jsonb` | 当前关键点数组 |
| `current_tags` | `jsonb` | 当前标签数组 |
| `current_topic_labels` | `jsonb` | 当前未在 API 暴露，创建时写空数组 |
| `current_relation_summary` | `jsonb` | 当前未在 API 暴露，创建时写空对象 |
| `importance_score` | `numeric(5,2)` | 当前创建时写 `50` |
| `latest_content_id` | `uuid fk -> note_contents.id` | 指向最新内容块 |
| `version` | `integer` | Proposal apply / rollback 时递增 |
| `extra_attributes` | `jsonb` | 当前创建时写空对象 |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

关系：

- `notes.latest_content_id -> note_contents.id`
- `review_states.note_id -> notes.id`
- `tasks.note_id -> notes.id`
- `change_proposals.note_id -> notes.id`
- `note_contents.note_id -> notes.id`

当前对外暴露：

- `GET /api/v1/notes`
- `GET /api/v1/notes/{id}`

未通过 Note API 暴露但真实存在：

- `note_type`
- `status`
- `current_tags`
- `current_topic_labels`
- `current_relation_summary`
- `importance_score`
- `version`
- `extra_attributes`

## 2.2 `note_contents`

表：`note_contents`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | 内容块主键 |
| `user_id` | `uuid not null` | 用户边界 |
| `note_id` | `uuid fk` | 所属 Note |
| `content_type` | check enum | `PRIMARY / UPDATE / EVIDENCE / TRANSCRIPT / CAPTURE_RAW` |
| `source_uri` | `text` | 来源地址 |
| `canonical_uri` | `text` | 当前通常与 `source_uri` 相同 |
| `source_snapshot` | `jsonb` | 来源元信息 |
| `raw_text` | `text` | 原始内容 |
| `clean_text` | `text` | 清洗后文本 |
| `analysis_result` | `jsonb` | Capture/Search 保存的结构化结果 |
| `is_current_view_source` | `boolean` | 当前内容是否为解释层来源 |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

真实用法：

- Capture 新建 Note 时写一条 `CAPTURE_RAW`
- Search “保存为证据”时追加一条 `EVIDENCE`
- 当前没有 `UPDATE` / `TRANSCRIPT` / `PRIMARY` 的实际写入路径

## 2.3 `capture_jobs`

表：`capture_jobs`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | Capture 作业 id |
| `user_id` | `uuid not null` | 用户边界 |
| `input_type` | check enum | `TEXT / URL` |
| `source_uri` | `text` | URL 输入来源 |
| `raw_input` | `text` | 原始文本输入 |
| `status` | `varchar(32)` | 代码里使用 `RECEIVED / EXTRACTING / ANALYZING / CONSOLIDATING / COMPLETED / FAILED`，但库里无 check |
| `extracted_payload` | `jsonb` | 抽取结果 |
| `analysis_result` | `jsonb` | AI 分析结果 |
| `consolidation_result` | `jsonb` | 写库结果，当前包含 `note_id` / `note_content_id` |
| `error_code` | `varchar(64)` | 当前保存失败原因 |
| `error_message` | `text` | 当前保存错误信息 |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

相关枚举：

- `CaptureInputType`: `TEXT / URL`
- `CaptureJobStatus`: `RECEIVED / EXTRACTING / ANALYZING / CONSOLIDATING / COMPLETED / FAILED`
- `CaptureFailureReason`: `EXTRACTION_FAILED / LLM_CALL_FAILED / LLM_OUTPUT_INVALID / CONSOLIDATION_FAILED`

## 2.4 `review_states`

表：`review_states`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | Review 状态 id |
| `user_id` | `uuid not null` | 用户边界 |
| `note_id` | `uuid fk` | 绑定 Note |
| `queue_type` | check enum | `SCHEDULE / RECALL` |
| `mastery_score` | `numeric(5,2)` | 掌握度 |
| `last_reviewed_at` | `timestamptz` | 最近复习时间 |
| `next_review_at` | `timestamptz` | 下一次时间 |
| `completion_status` | check enum | `COMPLETED / PARTIAL / NOT_STARTED / ABANDONED` |
| `completion_reason` | nullable check enum | `TIME_LIMIT / TOO_HARD / VAGUE_MEMORY / DEFERRED` |
| `unfinished_count` | `integer` | 未完成累计次数 |
| `retry_after_hours` | `integer` | 回忆补强重试间隔 |
| `review_meta` | `jsonb` | 保存 `self_recall_result` 与 `note` |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

相关枚举：

- `ReviewQueueType`: `SCHEDULE / RECALL`
- `ReviewCompletionStatus`: `COMPLETED / PARTIAL / NOT_STARTED / ABANDONED`
- `ReviewCompletionReason`: `TIME_LIMIT / TOO_HARD / VAGUE_MEMORY / DEFERRED`
- `ReviewSelfRecallResult`: `GOOD / VAGUE / FAILED`

状态机现实：

- `SCHEDULE` 与 `RECALL` 不是一个字段切换，而是同一 Note 下的两条 queue row
- `self_recall_result` 和 `note` 不在表列里，而是在 `review_meta` JSON 中
- `listToday` 会为每个 Note 自动补一条缺失的 `SCHEDULE` 记录

## 2.5 `tasks`

表：`tasks`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | Task 主键 |
| `user_id` | `uuid not null` | 用户边界 |
| `note_id` | `uuid fk nullable` | 关联 Note |
| `task_source` | check enum | `SYSTEM / USER` |
| `task_type` | `varchar(64)` | 任务类型字符串 |
| `title` | `varchar(255)` | 标题 |
| `description` | `text` | 描述 |
| `status` | check enum | `TODO / IN_PROGRESS / DONE / SKIPPED / CANCELLED` |
| `priority` | `integer` | 优先级 |
| `due_at` | `timestamptz nullable` | 截止时间 |
| `related_entity_type` | `varchar(64)` | 代码枚举为 `NOTE / IDEA / REVIEW / NONE`，但库里无 check |
| `related_entity_id` | `uuid nullable` | 关联实体 id，无外键 |
| `extra_attributes` | `jsonb` | 当前未在逻辑中使用 |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

相关枚举：

- `TaskSource`: `SYSTEM / USER`
- `TaskStatus`: `TODO / IN_PROGRESS / DONE / SKIPPED / CANCELLED`
- `TaskRelatedEntityType`: `NOTE / IDEA / REVIEW / NONE`

状态机现实：

- 当前真实写入路径只覆盖 `TODO -> DONE` 与 `TODO -> SKIPPED`
- `IN_PROGRESS / CANCELLED` 只是枚举可用值，没有 controller 路径

## 2.6 `change_proposals`

表：`change_proposals`

真实字段：

| 字段 | 类型/约束 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | Proposal id |
| `user_id` | `uuid not null` | 用户边界 |
| `note_id` | `uuid fk` | 目标 Note |
| `trace_id` | `uuid fk nullable` | 关联 trace |
| `proposal_type` | `varchar(64)` | 当前真实值包括 `REFRESH_INTERPRETATION`、`SEARCH_EVIDENCE_REFRESH_INTERPRETATION` |
| `target_layer` | check enum | `INTERPRETATION / METADATA / RELATION` |
| `risk_level` | check enum | `LOW / MEDIUM / HIGH` |
| `diff_summary` | `text` | 变更摘要 |
| `before_snapshot` | `jsonb` | 变更前快照 |
| `after_snapshot` | `jsonb` | 变更后快照 |
| `source_refs` | `jsonb` | 来源引用 |
| `rollback_token` | `varchar(128)` | apply 后写入 |
| `status` | check enum | `PENDING_REVIEW / APPLIED / REJECTED / ROLLED_BACK` |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |

相关枚举：

- `ChangeProposalTargetLayer`: `INTERPRETATION / METADATA / RELATION`
- `ChangeProposalRiskLevel`: `LOW / MEDIUM / HIGH`
- `ChangeProposalStatus`: `PENDING_REVIEW / APPLIED / REJECTED / ROLLED_BACK`

状态机现实：

- `generate` 创建 `PENDING_REVIEW`
- `apply` 更新 Note 解释层并改为 `APPLIED`
- `rollback` 恢复 `before_snapshot` 并改为 `ROLLED_BACK`
- 当前没有显式 reject API

## 2.7 `agent_traces`

表：`agent_traces`

真实字段：

| 字段 | 类型 | 现实语义 |
| --- | --- | --- |
| `id` | `uuid pk` | trace id |
| `user_id` | `uuid` | 用户边界 |
| `entry_type` | `varchar(64)` | 入口类型 |
| `goal` | `text` | 目标描述 |
| `root_entity_type` | `varchar(64)` | 根实体类型 |
| `root_entity_id` | `uuid` | 根实体 id |
| `status` | `varchar(32)` | 当前代码写 `RUNNING / COMPLETED / FAILED`，库里无 check |
| `orchestrator_state` | `jsonb` | 过程状态 |
| `worker_sequence` | `jsonb` | worker 顺序 |
| `result_summary` | `text` | 结果摘要 |
| `started_at` | `timestamptz` | 开始时间 |
| `ended_at` | `timestamptz` | 结束时间 |
| `created_at` | `timestamptz` | 创建时间 |

真实 `entry_type` 来源：

- `CAPTURE`
- `SEARCH_QUERY`
- `SEARCH_EVIDENCE_SAVE`
- `SEARCH_PROPOSAL_GENERATE`
- `CHANGE_PROPOSAL_GENERATE`
- `CHANGE_PROPOSAL_APPLY`
- `CHANGE_PROPOSAL_ROLLBACK`
- `TASK_CREATE`
- `TASK_COMPLETE`
- `TASK_SKIP`
- `REVIEW_AI_PREP`
- `REVIEW_AI_FEEDBACK`
- `REVIEW_COMPLETE`

## 2.8 `tool_invocation_logs`

表：`tool_invocation_logs`

真实字段：

- `id`
- `user_id`
- `trace_id`
- `tool_name`
- `status`
- `input_digest`
- `output_digest`
- `latency_ms`
- `error_code`
- `error_message`
- `created_at`

真实 tool name 例子：

- `capture.extract.text`
- `capture.extract.url`
- `capture.analysis.openai_compatible`
- `capture.analysis.ollama`
- `capture.note-consolidator`
- `search.execute`
- `search.external-supplement-generator`
- `search.ai-enhancement`
- `search.evidence.save`
- `search.proposal.generate`
- `proposal.interpretation-generator`
- `proposal.apply`
- `proposal.rollback`
- `review.ai-prep`
- `review.ai-feedback`
- `review.complete`

## 2.9 `user_action_events`

表：`user_action_events`

真实字段：

- `id`
- `user_id`
- `event_type`
- `entity_type`
- `entity_id`
- `session_id`
- `trace_id`
- `payload`
- `created_at`

真实事件类型样例：

- `CAPTURE_SUBMITTED`
- `NOTE_CREATED_FROM_CAPTURE`
- `SEARCH_EXECUTED`
- `SEARCH_EXTERNAL_SUPPLEMENTS_GENERATED`
- `SEARCH_AI_ENHANCEMENT_DEGRADED`
- `SEARCH_EVIDENCE_SAVED`
- `SEARCH_PROPOSAL_CREATED`
- `CHANGE_PROPOSAL_CREATED`
- `CHANGE_PROPOSAL_APPLIED`
- `CHANGE_PROPOSAL_ROLLED_BACK`
- `TASK_CREATED`
- `TASK_COMPLETED`
- `TASK_SKIPPED`
- `REVIEW_PREP_RENDERED`
- `REVIEW_PREP_DEGRADED`
- `REVIEW_COMPLETED`
- `REVIEW_PARTIAL`
- `REVIEW_NOT_STARTED`
- `REVIEW_ABANDONED`
- `SYSTEM_TASK_CREATED_FROM_REVIEW`
- `SYSTEM_TASK_UPDATED_FROM_REVIEW`
- `SYSTEM_TASK_COMPLETED_FROM_REVIEW`
- `REVIEW_FEEDBACK_GENERATED`
- `REVIEW_EXTENSION_SUGGESTIONS_GENERATED`
- `REVIEW_FOLLOW_UP_TASK_SUGGESTED`

## 2.10 Search 相关对象

Search 没有独立持久化表。真实对象分成两类：

持久化复用：

- `notes`
- `note_contents`
- `review_states`
- `change_proposals`

运行时对象：

- `SearchRepository.SearchCandidate`
- `SearchApplicationService.SearchView`
- `SearchExactMatchView`
- `SearchRelatedMatchView`
- `ExternalSupplementView`
- `SearchAiEnhancer.SearchAiEnhancementRequest`
- `RelatedEnhancement`
- `ExternalEnhancement`
- `SearchGovernanceApplicationService.SearchSupplement`

现实含义：

- `exact_matches` / `related_matches` 来自本地 Note 数据
- `external_supplements` 不是外部 provider 真实抓取结果，而是基于 query 构造的 deterministic stub seed，再经 AI 做最小字段增强

## 2.11 当前仍缺失的 Phase 3 收口对象

当前代码中已经存在最小 Idea 主线，但以下“完整收口所需对象”仍不存在：

- `POST /api/v1/ideas/{id}/start`
- `POST /api/v1/ideas/{id}/archive`
- `POST /api/v1/ideas/{id}/reopen`
- 与上述动作配套的 controller DTO / application service / state transition command
- Web 侧 Idea Create 表单入口
- Web 侧 Promote to Plan / Archive / Reopen 交互
- 对应生命周期动作的 trace / user event / proposal 治理补强

## 3. 状态机现实清单

## 3.1 CaptureJob

真实状态机：

- `RECEIVED -> EXTRACTING -> ANALYZING -> CONSOLIDATING -> COMPLETED`
- 任一阶段失败进入 `FAILED`

失败原因：

- `EXTRACTION_FAILED`
- `LLM_CALL_FAILED`
- `LLM_OUTPUT_INVALID`
- `CONSOLIDATION_FAILED`

## 3.2 Review

真实状态维度：

- queue：`SCHEDULE / RECALL`
- completion：`COMPLETED / PARTIAL / NOT_STARTED / ABANDONED`
- reason：`TIME_LIMIT / TOO_HARD / VAGUE_MEMORY / DEFERRED`

现实特征：

- `SCHEDULE` 完成与 `RECALL` 补强是两条状态记录
- `PARTIAL`、`NOT_STARTED`、`ABANDONED` 会影响 `unfinished_count`、`retry_after_hours`、`next_review_at`
- Review 完成可派生或更新一个 `task_type = REVIEW_FOLLOW_UP` 的 system task

## 3.3 Task

真实状态：

- `TODO`
- `IN_PROGRESS`
- `DONE`
- `SKIPPED`
- `CANCELLED`

当前开放动作：

- 创建时进入 `TODO`
- `POST /complete` 进入 `DONE`
- `POST /skip` 进入 `SKIPPED`

## 3.4 ChangeProposal

真实状态：

- `PENDING_REVIEW`
- `APPLIED`
- `REJECTED`
- `ROLLED_BACK`

当前开放动作：

- 创建
- apply
- rollback

## 3.5 NoteContentType

枚举存在：

- `PRIMARY`
- `UPDATE`
- `EVIDENCE`
- `TRANSCRIPT`
- `CAPTURE_RAW`

当前真实写入：

- `CAPTURE_RAW`
- `EVIDENCE`

## 3.6 Idea

当前没有真实状态机实现。

## 4. API 现实清单

| Route | Request DTO / 参数 | Response DTO | 当前语义 | 前端调用 |
| --- | --- | --- | --- | --- |
| `GET /api/v1/health` | 无 | `ApiEnvelope<Map<String,String>>` | 健康检查 | 否 |
| `POST /api/v1/captures` | `CreateCaptureRequest` | `CaptureResponse` | TEXT/URL capture，同步跑完 extraction + AI + consolidate | 是 |
| `GET /api/v1/captures/{id}` | path + `user_id` | `CaptureResponse` | 回看 capture job 结果 | 否 |
| `GET /api/v1/notes` | `user_id` | `List<NoteSummaryResponse>` | 列出 Note 当前解释层摘要 | 是 |
| `GET /api/v1/notes/{id}` | path + `user_id` | `NoteDetailResponse` | Note 详情 + evidence blocks | 是 |
| `POST /api/v1/notes/{noteId}/change-proposals` | `CreateChangeProposalRequest` | `ChangeProposalResponse` | 基于 Note 最新内容生成解释层 proposal | 是 |
| `GET /api/v1/notes/{noteId}/change-proposals` | path + `user_id` | `List<ChangeProposalResponse>` | 列出 Note proposals | 是 |
| `POST /api/v1/notes/{noteId}/change-proposals/{proposalId}/apply` | `ApplyChangeProposalRequest` | `ChangeProposalResponse` | 应用 proposal 到 Note 解释层 | 是 |
| `POST /api/v1/change-proposals/{proposalId}/rollback` | `RollbackChangeProposalRequest` | `ChangeProposalResponse` | 回滚已应用 proposal | 是 |
| `GET /api/v1/search` | `user_id` + `query` | `SearchResponse` | exact/related/external 三分栏搜索 | 是 |
| `POST /api/v1/search/notes/{noteId}/evidence` | `SearchSupplementActionRequest` | `SearchEvidenceResponse` | 将 external supplement 落为 `EVIDENCE` | 是 |
| `POST /api/v1/search/notes/{noteId}/change-proposals` | `SearchSupplementActionRequest` | `ChangeProposalResponse` | 基于 supplement 生成 proposal | 是 |
| `GET /api/v1/reviews/today` | `user_id` | `List<ReviewTodayItemResponse>` | Today Review 列表；主查询返回基础字段，AI 字段保留兼容但不再同步填充 | API client 有，App 未直接用 |
| `GET /api/v1/reviews/{reviewItemId}/prep` | path + `user_id` | `ReviewPrepResponse` | 独立读取单条 Review 的 AI prep 视图 | 是 |
| `GET /api/v1/reviews/{reviewItemId}/feedback` | path + `user_id` | `ReviewFeedbackResponse` | 独立读取单条 Review 的 AI feedback | 是 |
| `POST /api/v1/reviews/{reviewItemId}/complete` | `CompleteReviewRequest` | `ReviewCompletionResponse` | 完成 Review 主业务；不再同步等待 AI feedback | 是 |
| `POST /api/v1/tasks` | `CreateTaskRequest` | `TaskResponse` | 创建用户任务 | 前端未暴露 |
| `GET /api/v1/tasks/today` | `user_id` + `timezone_offset` | `List<TaskResponse>` | 今日任务列表 | API client 有，App 未直接用 |
| `POST /api/v1/tasks/{taskId}/complete` | `UpdateTaskStatusRequest` | `TaskResponse` | 完成任务 | 前端未暴露 |
| `POST /api/v1/tasks/{taskId}/skip` | `UpdateTaskStatusRequest` | `TaskResponse` | 跳过任务 | 前端未暴露 |
| `GET /api/v1/workspace/today` | `user_id` + `timezone_offset` | `WorkspaceTodayResponse` | 聚合 today reviews + tasks；不再同步等待 Review AI | 是 |
| `GET /api/v1/workspace/upcoming` | `user_id` + `timezone_offset` | `WorkspaceUpcomingResponse` | 聚合 upcoming reviews + tasks | 是 |

## 5. 前端功能现实清单

当前前端是一个单页工作台，核心表面都在 `web/src/App.tsx`。

| 功能 | 状态 | AI | 现实说明 |
| --- | --- | --- | --- |
| 用户 `user_id` 切换 | implemented | 否 | 本地存储切换上下文 |
| Capture TEXT/URL 提交 | implemented | 是 | 真调用 `POST /api/v1/captures` |
| Capture loading / result 卡片 | implemented | 是 | 显示状态、失败原因、analysis preview |
| Note 列表 | implemented | 否 | 真调用 `GET /api/v1/notes` |
| Note 详情 | implemented | 否 | 真调用 `GET /api/v1/notes/{id}` |
| Evidence 展示 | implemented | 否 | 读取 `evidence_blocks` |
| Proposal 列表/生成/应用/回滚 | implemented | 否 | 走真实接口 |
| Search 三分栏 | implemented | 部分 | exact/related 真基于本地数据，external supplement 为 stub seed + AI 增强 |
| Search 保存为证据 | implemented | 部分 | 走真实接口，落 `EVIDENCE` |
| Search 生成更新建议 | implemented | 部分 | 仅 `is_ai_enhanced=true` 可用 |
| Workspace Today | implemented | 部分 | 真调用聚合接口；首屏先显示基础 review/task，Review AI 改为按需读取 |
| Workspace Upcoming | implemented | 否 | 真调用聚合接口 |
| Review prep 懒加载 | implemented | 是 | 展开某条 Review 表单时调用 `GET /reviews/{id}/prep`，只影响当前卡片 |
| Review 完成表单 | implemented | 部分 | 真调用 `POST /reviews/{id}/complete`，主业务成功后再刷新工作台 |
| Review AI 反馈 banner | implemented | 是 | complete 成功后自动调用 `GET /reviews/{id}/feedback`，独立回填反馈区 |
| Task 列表展示 | implemented | 否 | Today/Upcoming 仅展示，不提供按钮动作 |
| User Task 创建 UI | not implemented | 否 | 后端有 API，前端没有入口 |
| Task complete/skip UI | not implemented | 否 | 后端有 API，前端没有入口 |
| Idea List / Detail / Assess / Generate Task | implemented | 部分 | 单页工作台已接通最小主路径；无 Create UI，生命周期后续动作未补齐 |

## 6. AI 接入现实清单

## 6.1 Provider 配置

真实 provider 与路由：

- `AiProvider`: `OPENAI_COMPATIBLE / OLLAMA`
- 默认：`noteops.ai.default-provider=OPENAI_COMPATIBLE`
- `application-local.yml` 默认切到 `OLLAMA`
- `application-prod.yml` 默认走 `OPENAI_COMPATIBLE`
- route:
  - `capture-analysis`
  - `search-enhancement`
  - `review-render`
  - `review-feedback`

## 6.2 Adapter / Interface / Implementation

| 层 | 真实对象 |
| --- | --- |
| 路由入口 | `AiClient` / `RoutingAiClient` |
| provider 接口 | `AiProviderClient` |
| OpenAI-compatible 实现 | `OpenAiCompatibleAiProviderClient` |
| Ollama 实现 | `OllamaAiProviderClient` |
| 请求/响应协议 | `AiRequest` / `AiResponse` / `AiResponseMode` |

## 6.3 Capture AI

真实接入点：

- `DefaultCaptureAnalysisClient`
- `CaptureAnalysisWorker`
- `CaptureAnalysisJsonSchema`

真实行为：

- 只在 `ANALYZING` 阶段调用 AI
- 请求严格要求 JSON object
- 返回必须可反序列化为 `CaptureAnalysisResult`
- 通过 `CaptureAnalysisResultValidator`

Trace / log / event：

- `agent_traces`: `entry_type=CAPTURE`
- `tool_invocation_logs`: extraction + ai + consolidator
- `user_action_events`: `CAPTURE_SUBMITTED`、`NOTE_CREATED_FROM_CAPTURE`

## 6.4 Search AI

真实接入点：

- `SearchAiEnhancer`
- `DefaultSearchAiEnhancer`

真实行为：

- 增强 `related_matches.relation_reason`
- 增强 `external_supplements.relation_label / keywords / summary_snippet`
- external supplement 本体不是外部 provider 抓取，而是 stub seed

Trace / log / event：

- `SEARCH_QUERY` trace
- `search.ai-enhancement` tool log
- `SEARCH_AI_ENHANCEMENT_DEGRADED` user event

## 6.5 Review AI

真实接入点：

- `ReviewAiAssistant`
- `DefaultReviewAiAssistant`

真实行为：

- `renderTodayItems`: 当前用于单条 Review prep 视图增强
- `buildCompletionFeedback`: 当前用于单条 Review feedback 独立读取
- AI 失败时降级，不中断主业务状态流转

Trace / log / event：

- `REVIEW_AI_PREP` trace
- `REVIEW_AI_FEEDBACK` trace
- `review.ai-prep`
- `review.ai-feedback`
- `REVIEW_PREP_RENDERED`
- `REVIEW_PREP_DEGRADED`
- `REVIEW_FEEDBACK_GENERATED`
- `REVIEW_EXTENSION_SUGGESTIONS_GENERATED`
- `REVIEW_FOLLOW_UP_TASK_SUGGESTED`

## 6.6 仅预留或未真正接入

- Idea AI：不存在
- Task AI：不存在
- Proposal AI：不存在，当前 proposal 生成是规则/文本处理，不走 AI
- Search 外部真实 provider：不存在
- Trend AI：不存在
- Preference learning AI：不存在
