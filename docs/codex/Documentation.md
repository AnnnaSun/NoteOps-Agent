# Documentation.md
# NoteOps-Agent Phase 3 文档基线（Idea 正式闭环）

## 1. 阶段定位

当前仓库进入 **Phase 3：Idea 正式闭环**。
但在正式推进 Idea 工作台前，已先落地一个 **Phase 3 前置补丁：Capture AI 最小闭环**，用于提供第一条真实可演示的 AI 主链路。

本阶段目标是承接 Phase 2 已完成的 Note / Search / Review / Task 主链路，把“知识沉淀”推进到“想法评估与执行规划”。

## 1.1 Capture AI 前置补丁（已完成）

这一步只覆盖 Capture，不代表 Search / Review / Task / Idea / Trend 已进入正式 AI 闭环。

### 当前真实能力
- `POST /api/v1/captures`
- `GET /api/v1/captures/{capture_job_id}`
- 输入类型仅支持：
  - `TEXT`
  - `URL`
- CaptureJob 状态机：
  - `RECEIVED`
  - `EXTRACTING`
  - `ANALYZING`
  - `CONSOLIDATING`
  - `COMPLETED`
  - `FAILED`
- 失败原因固定为：
  - `EXTRACTION_FAILED`
  - `LLM_CALL_FAILED`
  - `LLM_OUTPUT_INVALID`
  - `CONSOLIDATION_FAILED`

### Capture AI 的边界
- AI 只允许插在 `ANALYZING` 阶段
- LLM 只输出结构化 `CaptureAnalysisResult`
- 默认永远新建 Note
- 原始输入只落 append-only `CAPTURE_RAW`
- AI 结果只更新 Note 当前解释层与 `analysis_result`
- 不允许模型直接覆盖 raw content
- 不做旧 Note 匹配 / 合并策略
- 不做 `UPDATE / APPEND / CONFLICT`

### CaptureAnalysisResult 字段
- `title_candidate`
- `summary`
- `key_points`
- `tags`
- `idea_candidate`
- `confidence`
- `language`
- `warnings`

### 当前 provider 配置
- `noteops.capture.ai.provider=DEEPSEEK|OLLAMA`
- `noteops.capture.ai.request-timeout`
- `noteops.capture.ai.deepseek.base-url`
- `noteops.capture.ai.deepseek.api-key`
- `noteops.capture.ai.deepseek.model`
- `noteops.capture.ai.ollama.base-url`
- `noteops.capture.ai.ollama.model`
- `noteops.capture.url.connect-timeout`
- `noteops.capture.url.read-timeout`
- `noteops.capture.url.max-response-bytes`
- `noteops.capture.url.max-text-length`
- `noteops.capture.url.user-agent`

运行时默认 provider 已切到 `DEEPSEEK`。当前配置仍兼容旧的 `OPENAI_BASE_URL / OPENAI_API_KEY / OPENAI_MODEL` 环境变量作为 fallback，仅用于平滑迁移，不再作为 canonical 配置名。

### AI Router 边界
当前 router 已从硬编码 provider 分支改为基于 provider 注册表的映射路由：
- 新增 provider 时，不需要再修改 router 主逻辑
- 只需补：
  - 新的 `CaptureAiProvider` 枚举值
  - 一个实现 `CaptureProviderClient` 的 provider client bean
  - 对应 provider 配置
  - 新增 provider 的窄测试

这意味着后续补 `Gemini / OpenAI / ...` 的成本已经明显降低，但当前仍不是通用多模型平台：
- provider 配置结构仍是显式字段，不是动态 registry
- 不支持按任务/质量/成本做模型路由
- 不支持 fallback chain、权重路由或 prompt registry

### 请求 / 响应语义
创建 Capture 的 canonical 请求字段为：
- `user_id`
- `source_type`
- `raw_text`
- `source_url`
- `title_hint`

后端继续兼容旧别名：
- `input_type`
- `raw_input`
- `source_uri`

Capture 响应 data 字段为：
- `capture_job_id`
- `source_type`
- `status`
- `note_id`
- `failure_reason`
- `analysis_preview`
- `created_at`
- `updated_at`

`trace_id` 保持放在统一 `ApiEnvelope.trace_id` 顶层，不重复写入 data。

### Capture 成功时的最小落库结果
- 创建一个新 Note
- 创建一条 `note_contents.content_type = CAPTURE_RAW`
- raw content 保存 TEXT 原文或 URL 文本快照
- `notes.title / current_summary / current_key_points / current_tags` 来自已校验的 AI 输出
- `latest_content_id` 回指新写入的 raw content
- 写入 `agent_traces`
- 写入 `tool_invocation_logs`
- 写入 `user_action_events`
- 关键阶段补齐结构化日志

### 本阶段关注的问题
- 哪些想法值得保留？
- 这些想法的目标用户和问题定义是什么？
- 最小验证路径是什么？
- 哪些想法值得立即拆成任务推进？

### 本阶段不解决的问题
- 趋势抓取与趋势排序闭环
- 完整偏好画像学习
- 高级推荐与打分系统
- 多端适配与高级工作台形态

## 2. 核心领域语义

## 2.1 Idea
Idea 是从知识到执行之间的中间层。
它不是纯灵感收集箱，也不是脱离 Note 的独立产品，而是面向后续任务化、验证化推进的候选单元。

### 核心字段
- `id`
- `user_id`
- `source_note_id`
- `title`
- `status`
- `assessment_result`
- `created_at`
- `updated_at`

### 来源
Phase 3 最小闭环支持两类来源：
1. 从 Note 派生
2. 独立创建

仅预留、不正式闭环：
1. `FROM_SEARCH`
2. `FROM_TREND_CANDIDATE`

## 2.2 Idea 状态机

本阶段使用以下状态：

- `CAPTURED`
- `ASSESSED`
- `PLANNED`
- `IN_PROGRESS`
- `ARCHIVED`

### 最小状态迁移
- 创建后进入 `CAPTURED`
- 完成评估后进入 `ASSESSED`
- 生成执行任务后进入 `PLANNED`
- 开始真实推进后进入 `IN_PROGRESS`
- 完成 / 暂停 / 放弃后进入 `ARCHIVED`
- 可选支持 `ARCHIVED -> PLANNED` 重新开启

## 2.3 Assessment Result

Phase 3 的 `assessment_result` 至少包含：

- `problem_definition`
- `target_user`
- `core_hypothesis`
- `mvp_validation_path`

本阶段不要求：
- 复杂评分模型
- 价值 / 风险 / 置信度精算
- 多轮自动迭代评估

## 2.4 Idea 与 Task 的关系

Idea 不是终点。
被评估后的 Idea 应能生成 system task，进入既有 Task / Today / Upcoming 链路。

### Task 生成规则
- `task_source = SYSTEM`
- `linked_entity_type = IDEA`
- `linked_entity_id = idea.id`

### 目标
让想法能进入真实执行层，而不是停留在展示层。

## 3. API 基线

### 3.1 创建 Idea
`POST /api/ideas`

### 3.2 从 Note 派生 Idea
`POST /api/notes/{noteId}/ideas`

### 3.3 Idea 列表
`GET /api/ideas`

可选过滤：
- `status`
- `source_note_id`

### 3.4 Idea 详情
`GET /api/ideas/{id}`

### 3.5 Assess Idea
`POST /api/ideas/{id}/assess`

### 3.6 从 Idea 生成 Task
`POST /api/ideas/{id}/generate-task`

### 3.7 Idea 开始推进
`POST /api/ideas/{id}/start`

### 3.8 Idea 归档
`POST /api/ideas/{id}/archive`

### 3.9 Idea 重新开启（可选）
`POST /api/ideas/{id}/reopen`

## 4. 前端工作台基线

Phase 3 最小工作台至少包含：

1. **Idea List**
    - 列出 title、status、source_note_id、updated_at
    - 支持基础状态筛选

2. **Idea Detail**
    - 展示 Idea 基本信息
    - 展示 assessment_result
    - 展示关联 Task（如已有）

3. **Assess 入口**
    - 能提交最小 assessment payload
    - 成功后刷新详情状态

4. **Generate Task 入口**
    - 一键生成 system task
    - 生成后可跳转或提示进入 Today / Upcoming 查看

不要求现在提供：
- board
- drag-and-drop
- 高级排序
- 多列 pipeline

## 5. 治理与可追溯性

## 5.1 Trace / Event / UserActionEvent
以下动作必须至少写入 trace 与 event，并在适用时记录 user_action_event：

- 创建 Idea
- 从 Note 派生 Idea
- Assess Idea
- Generate Task
- 状态迁移
- Archive / Reopen
- 关键字段更新

### user_action_event 建议
- `entity_type = IDEA`
- `action_type` 例如：
    - `IDEA_CREATED`
    - `IDEA_DERIVED_FROM_NOTE`
    - `IDEA_ASSESSED`
    - `IDEA_TASK_GENERATED`
    - `IDEA_STARTED`
    - `IDEA_ARCHIVED`

## 5.2 Proposal 语义

Phase 3 不要求所有 Idea 变更都强制经过完整 proposal 流程。
但以下原则必须遵守：

1. 关键变更不能完全静默写库
2. 至少保留 before / after 摘要
3. 关键评估写回应保留 trace 与 decision summary
4. 若仓库已有成熟 proposal 机制，优先沿用

## 5.3 结构化日志

以下行为必须补齐结构化日志：

- create idea
- derive idea from note
- assess idea
- generate task from idea
- idea state transition
- archive / reopen
- trace / event write failure
- external dependency failure（如本阶段涉及）

### 最小日志字段
- `trace_id`
- `user_id`
- `idea_id`
- `note_id`（如适用）
- `task_id`（如适用）
- `action`
- `result`
- `duration_ms`（如适用）
- `error_code`
- `error_message`

## 6. 与其他阶段的边界

## 6.1 已完成基础（来自前置阶段）
- Note 主链路
- Review 双池
- Search 三分栏
- Today / Upcoming
- Task 基础闭环
- Proposal / Trace / Event 最小治理链路

## 6.2 本阶段新增
- Idea 正式实体
- Idea Assess
- Idea -> Task
- Idea 工作台

## 6.3 后续阶段继续处理
- Trend 抓取、聚合、排序、候选池
- Preference Learning 正式闭环
- Trend 与 Idea 的自动联动
- 高级推荐系统

## 7. 完成定义

只有满足以下条件，Phase 3 才能标记为“已完成最小闭环”：

1. 能独立创建 Idea
2. 能从 Note 派生 Idea
3. 能进行最小 Assess
4. 能从 Idea 生成真实 Task
5. Task 能进入 Today / Upcoming
6. Idea 状态机能跑到 `ARCHIVED`
7. 关键动作具备 trace / event / structured log
8. 前端工作台走真实接口

## 8. Deferred Backlog

本阶段明确后置，但后面必须补回：

1. Trend 正式闭环
2. user_preference_profiles 正式学习
3. Idea scoring / ranking
4. Trend candidate -> Idea 自动流转
5. richer assessment template
6. Idea board / pipeline
7. 基于 preference 的 Idea 排序与推荐
8. 旧 Note 匹配与合并策略
9. `UPDATE / APPEND / CONFLICT` 全量决策
10. Search 的 AI relation / evidence 闭环
11. Review 的 AI recall / extension 闭环
12. Task 的 AI 派生策略
13. Idea candidate 的正式生命周期
14. URL extraction robustness
15. 多模型路由与 prompt 模板治理
