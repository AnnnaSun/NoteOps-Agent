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

本仓库对应的产品不是通用笔记软件，而是 **以 Note 为第一公民的多 Agent Knowledge-to-Action 系统**。当前开发基线已进入 **Phase 3：Idea Lifecycle / Idea Workspace**，后续实现必须优先满足“可控、可追溯、可验证”，而不是一次性铺开全部未来能力。

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
    - idea create / assess / promote_to_plan / archive / reopen
    - idea task generation / idea follow-up decision
    - 任何会写入 `agent_traces`、`tool_invocation_logs`、`user_action_events` 的关键动作
  - 日志字段至少应包含：
    - `trace_id`
    - `user_id`
    - 模块名或 service / controller 名
    - 接口路径或命令名
    - 关键业务标识，如 `note_id`、`idea_id`、`task_id`、`proposal_id`
    - `action`
    - `result`
    - `duration_ms`（如适用）
    - `error_code`、`error_message`（失败时）
  - 同一条请求链路中的日志应可通过 `trace_id` 关联，保证问题可以从入口追到落库、外调和状态变更。
  - 新增涉及核心状态变更、外部调用、调度决策的实现时，如未补日志，视为未完成最小闭环。

---

## 3. 当前阶段边界（Phase 3）

当前以 **Phase 3：Idea Lifecycle / Idea Workspace** 为唯一开发目标。

### 3.1 Phase 3 必做

1. Idea 生命周期真正落地：
  - `CAPTURED`
  - `ASSESSED`
  - `PLANNED`
  - `IN_PROGRESS`
  - `ARCHIVED`

2. Idea 创建最小闭环：
  - 支持 `FROM_NOTE`
  - 支持独立创建
  - 允许保留未来来源的扩展位，但当前不实现 Trend 驱动正式流

3. Idea Assess 最小闭环：
  - 输出 `problem_statement`
  - 输出 `target_user`
  - 输出 `core_hypothesis` 或等价假设摘要
  - 输出 `mvp_validation_path`
  - 输出 `next_actions`

4. Idea 与 Task 的主链路打通：
  - Assess 后可生成 `SYSTEM` task
  - 生成的 task 能进入 Today / Upcoming
  - task 与 `IDEA` 关联可追溯

5. Idea Workspace 最小可用：
  - `Idea List`
  - `Idea Detail`
  - `Assess` 入口
  - `Promote to Plan / Archive / Reopen` 的基础交互

6. Idea 相关 Proposal / Event / Trace 补强：
  - 关键字段更新需可追溯
  - assess、状态迁移、task 派生必须写 trace / event / logs
  - 高风险更新继续走 proposal 治理链路

7. API / DTO / 文档同步到 Phase 3 语义

8. Web 页面最小可用：Idea 主路径连真实接口

### 3.2 Phase 3 可预留但不做正式闭环

1. Trend source registry 与 Trend Inbox
2. `user_preference_profiles` 正式画像重算与稳定排序影响
3. 复杂 Idea 打分算法、市场评分、竞品自动分析
4. Kanban / Pipeline 高级视图
5. 复杂任务批量拆解
6. 真实外部 research provider 的正式接入（可先保留接口与 stub/mock）

### 3.3 Phase 3 明确不做

1. 完整账号体系
2. 原生 Android / iOS
3. 原始音视频处理
4. 任意网站自由抓取
5. 完整导出中心
6. Trend 正式闭环
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

Review 仍保持 Phase 2 既有语义，不允许在 Phase 3 回退：
- 双池：`SCHEDULE` / `RECALL`
- 完成状态：
  - `COMPLETED`
  - `PARTIAL`
  - `NOT_STARTED`
  - `ABANDONED`
- 完成原因：
  - `TIME_LIMIT`
  - `TOO_HARD`
  - `VAGUE_MEMORY`
  - `DEFERRED`

Review 默认展示对象仍是 **当前摘要 + 关键点 + 必要延伸**，不是整条 Note 全文直出。

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

Phase 3 中，Idea 派生 task 必须显式设置：
- `task_source`
- `task_type`
- `related_entity_type=IDEA`
- `related_entity_id=<idea_id>`
- `due_at`（如存在计划时间）

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
Phase 3 中，Idea 的关键字段更新也不得绕开 proposal / trace / event 治理链路。

### 7.5 Idea

Idea 是独立实体，但不能脱离主知识链路。

Idea 至少支持：
- `FROM_NOTE`
- `MANUAL`

Idea 状态机必须保持：
- `CAPTURED`
- `ASSESSED`
- `PLANNED`
- `IN_PROGRESS`
- `ARCHIVED`

Idea Assess 输出至少包含：
- `problem_statement`
- `target_user`
- `core_hypothesis` 或等价字段
- `mvp_validation_path`
- `next_actions`

Assess 结果可以保存到 `assessment_result`，但关键状态推进、任务派生、人工改写都必须可追溯。

### 7.6 Search / Trend / Preference 的阶段边界

- Search 保持已有能力，但 Phase 3 不以 Search 为主线扩展目标
- Trend 仍属于后续阶段，不应提前做正式 Inbox / 转化流
- `user_preference_profiles` 仍可预留或只读占位，不做正式学习闭环与静默生效

### 7.7 CaptureJob

V1 仍只支持：

- `TEXT`
- `URL`

Phase 3 不应为了 Idea 扩大 Capture 输入边界。

---

## 8. 架构与分层要求

### 8.1 后端分层

后端默认采用如下边界：

1. API / Controller 层  
   负责 DTO、参数校验、响应 envelope、错误码映射。

2. Application / Orchestration 层  
   负责编排 Idea / Task / Proposal / Trace 等流程、写 Trace、组织规则决策。

3. Domain 层  
   负责 Note、Review、Task、Idea、Proposal 等核心业务规则与状态机。

4. Persistence 层  
   负责 Repository、ORM 映射、查询与迁移。

不要把“主编排 Agent”写成一个无边界的大类。  
**Orchestrator 只负责路由、状态推进、治理与 trace。具体处理放到 Domain Service / Worker Agent。**

### 8.2 Worker Agent 边界

当前 Phase 3 只允许最小 Worker Agent 扩展点，不要把未来 Phase 4/5/6 的完整逻辑提前实现。

最小边界：
- Capture Worker
- Review Worker
- Search Worker
- Idea Worker
- Workspace Aggregation Worker（如 Today / Idea 聚合需要）
