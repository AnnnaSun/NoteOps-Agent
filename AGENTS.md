# AGENTS
# AGENTS.md

## 1. 文档目的

本文件是 NoteOps 仓库级执行契约。Codex 在执行任何任务前，必须先遵守这里的边界、优先级、目录路由、验证要求与完成定义。

本仓库对应的产品不是通用笔记软件，而是 **以 Note 为第一公民的多 Agent Knowledge-to-Action 系统**。当前开发基线已冻结到可重新启动 Phase 1 的版本，后续实现必须优先满足“可控、可追溯、可验证”，而不是一次性铺开全部未来能力。

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

---

## 3. 当前阶段边界（Phase 1 冻结）

当前以 **Phase 1：知识内核 / Knowledge Kernel** 为唯一开发目标。

### 3.1 Phase 1 必做

1. Web 基础骨架
2. TEXT / URL 两类 Capture 输入
3. CaptureJob 状态流转
4. Note 主记录与 NoteContent 历史块
5. ReviewState 基础闭环
6. Task 基础闭环（System Task + User Task）
7. ChangeProposal 基础闭环（生成 / 查看 / 应用 / 回退）
8. AgentTrace 与 ToolInvocationLog
9. UserActionEvent 基础埋点
10. PostgreSQL 主库与最小可运行 API

### 3.2 Phase 1 不做

1. 完整账号体系
2. 原生 Android / iOS
3. 原始音视频处理
4. 任意网站自由抓取
5. 完整导出中心
6. 全量 Preference Learning 画像计算
7. Trend 正式闭环
8. Idea 正式生命周期闭环（可预留模型或接口，但不应抢占 Phase 1 主线）

---

## 4. 仓库与目录路由

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

## 5. Source of Truth 优先级

当信息冲突时，按以下优先级裁决：

1. 当前用户明确任务
2. 本文件 `AGENTS.md`
3. `docs/codex/Prompt.md`
4. `docs/codex/Plan.md`
5. 已冻结的产品/架构/表结构/API 文档
6. 现有代码与测试
7. 旧注释、旧草稿、推测

禁止基于“可能未来会需要”自行扩大当前任务范围。

---

## 6. 领域建模硬约束

### 6.1 Note / NoteContent

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

### 6.2 Review

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

Review 默认展示对象是 **当前摘要 + 关键点 + 必要延伸**，不是整条 Note 全文直出。

### 6.3 Task

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

### 6.4 ChangeProposal

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

### 6.5 CaptureJob

V1 只支持：

- `TEXT`
- `URL`

状态机至少覆盖：

- `RECEIVED`
- `EXTRACTING`
- `ANALYZING`
- `CONSOLIDATING`
- `COMPLETED`
- `FAILED`

---

## 7. 架构与分层要求

### 7.1 后端分层

后端默认采用如下边界：

1. API / Controller 层  
   负责 DTO、参数校验、响应 envelope、错误码映射。

2. Application / Orchestration 层  
   负责编排 Capture / Review / Search 等流程、写 Trace、组织规则决策。

3. Domain 层  
   负责 Note、Review、Task、Proposal 等核心业务规则与状态机。

4. Persistence 层  
   负责 Repository、ORM 映射、查询与迁移。

不要把“主编排 Agent”写成一个无边界的大类。  
**Orchestrator 只负责路由、状态推进、治理与 trace。具体处理放到 Domain Service / Worker Agent。**

### 7.2 Worker Agent 边界

当前 Phase 1 只允许保留最小 Worker Agent 扩展点，不要把未来 Phase 3/4/5 的完整逻辑提前实现。

最小边界：
- Capture Worker
- Review Worker（基础调度）
- Search/Idea/Trend 仅保留接口或空实现扩展点时，必须标明“非当前阶段完成项”

---

## 8. API 契约要求

### 8.1 统一 Envelope

所有 REST 响应应统一使用 envelope，至少包含：

- `success`
- `trace_id`
- `data` 或 `error`
- `meta.server_time`

### 8.2 Phase 1 核心接口范围

至少优先覆盖：

- `POST /api/v1/captures`
- `GET /api/v1/captures/{id}`
- `GET /api/v1/notes`
- `GET /api/v1/notes/{id}`
- `GET /api/v1/reviews/today`
- `POST /api/v1/reviews/{review_item_id}/complete`
- `POST /api/v1/tasks`
- `GET /api/v1/tasks/today`
- `POST /api/v1/tasks/{task_id}/complete`
- `POST /api/v1/tasks/{task_id}/skip`
- `POST /api/v1/notes/{note_id}/change-proposals/{proposal_id}/apply`
- `POST /api/v1/change-proposals/{id}/rollback`

搜索、Idea、Trend 可以预留，但不应抢占当前主交付。

---

## 9. 搜索与外部证据边界

- 搜索结果目标形态冻结为三分栏：
    - `exact_matches`
    - `related_matches`
    - `external_supplements`
- 外部结果只允许：
    1. 形成 evidence block
    2. 形成 proposal
    3. 提示冲突 / 背景补充 / 延伸阅读
- 外部证据不得直接覆盖本地 `current_summary`

---

## 10. 离线边界

允许离线的动作仅限：

1. 完成 review
2. 写轻量备注
3. 改 tag

禁止离线：

1. 外部检索
2. URL 抽取
3. 深度 LLM 分析
4. proposal apply / rollback
5. 大规模结构编辑

客户端回传必须走 action log / pending actions，不得直接覆盖最终状态。

---

## 11. 代码变更规则

执行任何任务时，必须遵守：

1. 只做当前任务最小闭环，不顺手扩写 Phase 2/3/4
2. 不做无关重构
3. 不静默更名核心字段、核心状态、接口路径
4. 改 schema 时必须同步检查 DTO、Repository、文档、测试
5. 改 API 时必须同步检查前端调用与响应结构
6. 改状态机时必须同步检查 trace、event、日志与错误码
7. 不允许声称“已验证”但没有实际运行

---

## 12. 文档同步规则

出现以下情况时，必须同步 `docs/` 下相关文档：

- 表结构变化
- 接口契约变化
- 状态机变化
- Phase 1 范围变化
- 目录结构变化
- 已冻结规则被进一步补丁确认

至少同步：
- `docs/codex/Documentation.md`
- 必要时补充 API / schema 说明

---

## 13. 验证要求

### 13.1 后端任务至少执行

- 受影响模块单元测试或集成测试
- 构建 / 编译
- 迁移校验（若涉及 DB）

### 13.2 前端任务至少执行

- 受影响页面或模块构建
- 类型检查 / lint（若项目已配置）
- 关键交互手工自检说明

### 13.3 没做验证时

必须明确写出：

- 哪些没验证
- 为什么没验证
- 风险在哪里

---

## 14. 输出格式要求

Codex 完成任务后，默认按以下格式汇报：

1. **本次完成**
2. **修改文件**
3. **验证结果**
4. **未覆盖风险 / 下一步**

禁止只回复“done”或“已完成”。

---

## 15. 明确禁止

禁止：

- 将原始正文设计成可被 proposal 直接重写
- 把 Task 只做 System Task，漏掉 User Task
- 把 Review 简化成单池 + 单布尔完成状态
- 省略 `user_id` 预留
- 省略 trace / event / proposal 的最小治理链路
- 将外部证据直接写入当前知识正文
- 为了快，跳过状态约束与错误码设计
- 未经要求提前做移动端、复杂权限、多租户实现

