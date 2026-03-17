# AGENTS
## Communication language

Always communicate with the user in Simplified Chinese unless explicitly asked otherwise.

Even if the user writes prompts in English, respond in Simplified Chinese.

Keep the following in their original language unless the user explicitly requests translation:
- source code
- identifiers
- class names
- function names
- file paths
- shell commands
- SQL
- API routes
- database tables and fields
- logs and error messages

For implementation summaries, validation reports, risks, blockers, and next steps, use Simplified Chinese.

Do not change the language of existing repository files unless the task explicitly requires documentation or copy changes.

## 1. 文档目的

本文件是 NoteOps 仓库级执行契约。Codex 在执行任何任务前，必须先遵守这里的边界、优先级、目录路由、验证要求与完成定义。

本仓库对应的产品不是通用笔记软件，而是 **以 Note 为第一公民的多 Agent Knowledge-to-Action 系统**。当前开发基线已进入 **Phase 2：Review / Search / Today Workspace**，后续实现必须优先满足“可控、可追溯、可验证”，而不是一次性铺开全部未来能力。

---

## 2. 根原则

### 2.1 产品与数据根原则

1. **Note 是第一公民**  
   Task、Review、ChangeProposal、Idea、Trend、Preference Learning 均围绕 Note 展开，不允许把 Task/Idea 设计成脱离主知识链路的独立系统。

2. **服务端 PostgreSQL 是唯一真相源**  
   客户端（Web/PWA/未来移动端）只负责缓存、离线操作记录和回传，不得将客户端本地状态视为最终真相。

3. **原始内容与当前解释层分离**
    - `note_contents`：保存原始内容、更新块、证据块、转写块等追加型历史内容。
    - `notes`：保存当前视图，如 `current_summary`、`current_key_points`、`current_tags`。
    - ChangeProposal 只能作用于解释层、元数据层、关系层，不得静默覆盖原始正文。

4. **自动化变更必须可治理**
    - 低风险：可建议、可一键应用、可撤销
    - 中风险：必须人工确认
    - 高风险：只提示冲突，不直接改写正文

5. **所有核心聚合预留 `user_id`**
   V1 可以是单用户运行，但模型、查询、索引、接口设计都必须保留未来多用户边界。

6. **阶段性跳过 ≠ 永久删除**  
   为了完成当前阶段最小闭环而暂时跳过的功能，必须记录到 deferred backlog / documentation 中，后续阶段必须补回，不允许因为“先不做”而永久遗漏。

### 2.7 可观测性根原则

7. **关键链路必须具备可监控、可定位、可关联的结构化日志**
    - 任何核心业务链路，不允许只靠零散 `console.log` 或无法关联上下文的纯文本输出。
    - 以下场景默认必须补充结构化日志：
        - 请求入口与核心命令入口
        - 状态迁移与状态机流转
        - 外部调用开始 / 成功 / 失败
        - proposal apply / rollback
        - review complete / partial / recall requeue
        - search external supplement 生成 evidence / proposal
        - task 创建、完成、跳过、重排
        - 任何会写入 `agent_traces`、`tool_invocation_logs`、`user_action_events` 的关键动作
    - 日志字段至少应包含：
        - `trace_id`
        - `user_id`
        - 模块名或 service / controller 名
        - 接口路径或命令名
        - 关键业务标识，如 `note_id`、`review_item_id`、`task_id`、`proposal_id`
        - `action`
        - `result`
        - `duration_ms`（如适用）
        - `error_code`、`error_message`（失败时）
    - 同一条请求链路中的日志应可通过 `trace_id` 关联，保证问题可以从入口追到落库、外调和状态变更。
    - 新增涉及核心状态变更、外部调用、调度决策的实现时，如未补日志，视为未完成最小闭环。

---

## 3. 当前阶段边界（Phase 2）

当前以 **Phase 2：Review / Search / Today Workspace** 为唯一开发目标。

### 3.1 Phase 2 必做

1. Review 双池工作流真正落地：`SCHEDULE` / `RECALL`
2. Review 完成语义落地：
    - `completion_status`：`COMPLETED` / `PARTIAL` / `NOT_STARTED` / `ABANDONED`
    - `completion_reason`：`TIME_LIMIT` / `TOO_HARD` / `VAGUE_MEMORY` / `DEFERRED`
3. Recall 用户反馈最小闭环：支持用户自评 + 简短备注
4. Today / Upcoming 工作台：同屏展示 ReviewItem 与 Task，并显式区分 `task_source`
5. User Task Phase 2 能力：创建、完成、跳过、Today 展示、按 `due_at` 排序进入 Upcoming
6. Search 三分栏结果：
    - `exact_matches`
    - `related_matches`
    - `external_supplements`
7. 外部补充证据治理：外部结果只能形成 `EVIDENCE` block 或 `ChangeProposal`，不得直接覆盖 `notes.current_*`
8. Proposal / Event / Trace 补强：Search 与 Review 导致的建议、证据、应用动作必须可追溯
9. API / DTO / 文档同步到 Phase 2 语义
10. Web 页面最小可用：Today / Review / Search 主路径连真实接口

### 3.2 Phase 2 可预留但不做正式闭环

1. `ideas` 正式生命周期
2. `user_preference_profiles` 正式画像计算与回写
3. Trend 正式闭环
4. 周视图 / 月视图 Calendar
5. 复杂智能评分、复杂推荐算法、复杂优先级学习
6. 真实外部搜索 provider 的正式接入（可先保留接口与 stub/mock）

### 3.3 Phase 2 明确不做

1. 完整账号体系
2. 原生 Android / iOS
3. 原始音视频处理
4. 任意网站自由抓取
5. 完整导出中心
6. Idea Card assess / task 派生正式流
7. Preference Learning 的成熟画像重算器

---

## 4. Deferred Backlog 规则

凡是因为最小闭环而推迟的能力，必须满足以下要求：

1. 在 `docs/codex/Documentation.md` 中有明确记录
2. 标明原因：为什么现在不做
3. 标明未来阶段：预期在哪个 Phase 补回
4. 标明影响：当前闭环少了什么
5. 不得在后续文档中被静默删除

---

## 5. 仓库与目录路由

除非实际仓库结构已经不同且用户明确要求，否则默认以下路由：

- `server/`：后端服务、领域模型、API、持久化、Agent 编排、测试
- `web/`：前端 Web 界面
- `docs/`：PRD、架构、表结构、接口、Codex 工作文档
- `docs/codex/`：当前 Codex 执行基线
- `server/src/main/resources/db/migration/`：数据库迁移
- `server/src/test/`：后端测试
- `web/src/`：前端实现

不要在一次任务里随意扩展新目录层级。目录结构如需调整，必须先说明理由，并同步文档。

---

## 6. Source of Truth 优先级

当信息冲突时，按以下优先级裁决：

1. 当前用户明确任务
2. 本文件 `AGENTS.md`
3. `docs/codex/Prompt.md`
4. `docs/codex/Plan.md`
5. `docs/codex/Documentation.md`
6. 已冻结的产品/架构/表结构/API 文档
7. 现有代码与测试
8. 旧注释、旧草稿、推测

禁止基于“可能未来会需要”自行扩大当前任务范围。

---

## 7. 领域建模硬约束

### 7.1 Note / NoteContent

- `notes` 保存当前解释层和当前展示层
- `note_contents` 保存原始内容与增量块
- 原始内容默认只追加，不覆盖
- `latest_content_id` 应可回指当前最新内容块
- `content_type` 至少覆盖：
    - `PRIMARY`
    - `UPDATE`
    - `EVIDENCE`
    - `TRANSCRIPT`
    - `CAPTURE_RAW`

### 7.2 Review

Review 必须支持双池语义：

- `SCHEDULE`
- `RECALL`

并至少支持以下完成状态：

- `COMPLETED`
- `PARTIAL`
- `NOT_STARTED`
- `ABANDONED`

完成原因至少支持：

- `TIME_LIMIT`
- `TOO_HARD`
- `VAGUE_MEMORY`
- `DEFERRED`

Phase 2 的 Recall 闭环至少包含：

- 用户自评结果
- 用户简短备注
- 与 review item 的可追溯关联

Review 默认展示对象是 **当前摘要 + 关键点 + 必要延伸**，不是整条 Note 全文直出。

### 7.3 Task

Task 统一承接两类来源：

- `SYSTEM`
- `USER`

Task 必须支持绑定以下对象：

- `NOTE`
- `IDEA`
- `REVIEW`
- `NONE`

Task 状态保持轻量：

- `TODO`
- `IN_PROGRESS`
- `DONE`
- `SKIPPED`
- `CANCELLED`

Task 在 Phase 2 必须支持 `due_at` 语义，以服务 Today / Upcoming 排序与展示。

### 7.4 ChangeProposal

ChangeProposal 必须显式包含：

- `target_layer`：
    - `INTERPRETATION`
    - `METADATA`
    - `RELATION`
- `risk_level`
- `before_snapshot`
- `after_snapshot`
- `diff_summary`
- `rollback_token`

ChangeProposal 不是正文覆盖器。

### 7.5 Search

Search 的结果合同必须显式区分：

- `exact_matches`
- `related_matches`
- `external_supplements`

外部补充结果不得静默改写 note 当前解释层。若需要影响解释层，必须通过：

1. 写入 `EVIDENCE` block，或
2. 生成 `ChangeProposal`

### 7.6 CaptureJob

V1 仍只支持：

- `TEXT`
- `URL`

状态机至少覆盖：

- `RECEIVED`
- `EXTRACTING`
- `ANALYZING`
- `CONSOLIDATING`
- `COMPLETED`
- `FAILED`

Phase 2 不应为了 Search/Review 扩大 Capture 输入边界。

---

## 8. 架构与分层要求

### 8.1 后端分层

后端默认采用如下边界：

1. API / Controller 层  
   负责 DTO、参数校验、响应 envelope、错误码映射。

2. Application / Orchestration 层  
   负责编排 Capture / Review / Search / Today 等流程、写 Trace、组织规则决策。

3. Domain 层  
   负责 Note、Review、Task、Proposal 等核心业务规则与状态机。

4. Persistence 层  
   负责 Repository、ORM 映射、查询与迁移。

不要把“主编排 Agent”写成一个无边界的大类。  
**Orchestrator 只负责路由、状态推进、治理与 trace。具体处理放到 Domain Service / Worker Agent。**

### 8.2 Worker Agent 边界

当前 Phase 2 只允许最小 Worker Agent 扩展点，不要把未来 Phase 3/4/5 的完整逻辑提前实现。

最小边界：
- Capture Worker
- Review Worker
- Search Worker（可 stub/mock 外部 provider）
- Workspace Aggregation Worker（如 Today 聚合需要）
