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

本仓库对应的产品不是通用笔记软件，而是 **以 Note 为第一公民的多 Agent Knowledge-to-Action 系统**。当前开发基线已进入 **Phase 4：Trend Source Registry / Trend Inbox / Trend Conversion**。后续实现必须优先满足“可控、可追溯、可验证”，而不是一次性铺开全部未来能力。

---

## 2. 根原则

### 2.1 产品与数据根原则

1. **Note 是第一公民**  
   Task、Review、ChangeProposal、Idea、Trend、Preference Learning 均围绕 Note 展开，不允许把 Task / Idea / Trend 设计成脱离主知识链路的独立系统。

2. **服务端 PostgreSQL 是唯一真相源**  
   客户端（Web / PWA / 未来移动端）只负责缓存、离线操作记录和回传，不得将客户端本地状态视为最终真相。

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

### 2.2 可观测性根原则

7. **关键链路必须具备可监控、可定位、可关联的结构化日志**
    - 任何核心业务链路，不允许只靠零散 `console.log` 或无法关联上下文的纯文本输出。
    - 以下场景默认必须补充结构化日志：
        - 请求入口与核心命令入口
        - 状态迁移与状态机流转
        - 外部调用开始 / 成功 / 失败
        - proposal apply / rollback
        - trend ingest / normalize / score / convert
        - trend ignore / save / promote 行为
        - task 创建、完成、跳过、重排
        - 任何会写入 `agent_traces`、`tool_invocation_logs`、`user_action_events` 的关键动作
    - 日志字段至少应包含：
        - `trace_id`
        - `user_id`
        - 模块名或 service / controller 名
        - 接口路径或命令名
        - 关键业务标识，如 `trend_item_id`、`note_id`、`idea_id`、`task_id`、`proposal_id`
        - `action`
        - `result`
        - `duration_ms`（如适用）
        - `error_code`、`error_message`（失败时）
    - 同一条请求链路中的日志应可通过 `trace_id` 关联，保证问题可以从入口追到落库、外调和状态变更。
    - 新增涉及核心状态变更、外部调用、调度决策的实现时，如未补日志，视为未完成最小闭环。

---

## 3. 当前阶段边界（Phase 4）

当前以 **Phase 4：Trend Source Registry / Trend Inbox / Trend Conversion** 为唯一开发目标。

### 3.1 Phase 4 必做

1. 最小 Trend source registry 真正落地：默认支持 `HN` 与 `GITHUB` 两类来源
2. 默认 Trend plan 真正落地：可按固定计划抓取少量高价值候选
3. Trend ingest 最小闭环：抓取、去重、归一化、落库 `trend_items`
4. Trend AI 最小分析闭环：
    - 生成简练摘要
    - 生成 topic tags / why-it-matters
    - 给出 `note_worthy` / `idea_worthy`
    - 给出 `suggested_action`
5. Trend Inbox 最小工作台：
    - 列表展示
    - 来源展示
    - 分数与摘要展示
    - 动作入口：`IGNORE` / `SAVE_AS_NOTE` / `PROMOTE_TO_IDEA`
6. Trend -> Note 转化闭环：保留来源链、必要 evidence、trace / event / log
7. Trend -> Idea 转化闭环：创建 Idea 后可显式进入现有 Idea assess 主链
8. Trend 相关 `agent_traces` / `tool_invocation_logs` / `user_action_events` 补齐
9. API / DTO / 文档同步到 Phase 4 语义
10. Web 页面最小可用：Trend Inbox 主路径连真实接口

### 3.2 Phase 4 可预留但不做正式闭环

1. 用户自定义复杂 Trend 规则编辑器
2. 多 provider 趋势采集平台化
3. 个性化偏好重排与在线学习
4. 自动静默转 Note / 转 Idea
5. 复杂评分算法、复杂趋势聚类、复杂主题漂移分析
6. 任意网站自由抓取
7. 高保真视觉重构与高级看板

### 3.3 Phase 4 明确不做

1. Preference Learning 正式画像重算器
2. PWA 正式离线趋势抓取
3. 原生 Android / iOS
4. 原始音视频处理
5. 完整导出中心
6. 无边界的网页抓取平台
7. Trend 驱动的自动大规模知识重写

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
6. 已冻结的产品 / 架构 / 表结构 / API 文档
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

### 7.2 Trend

Trend 必须支持“来源注册 + 候选入箱 + 用户决策 + 转化”语义。

当前阶段至少支持：
- `HN`
- `GITHUB`

TrendItem 至少要保留：
- `source_type`
- `title`
- `url`
- `summary`
- `normalized_score` 或等价综合分
- 结构化分析载荷（如 `analysis_payload` / `extra_attributes`）

Trend 不允许被简化为“纯抓取结果列表”。至少要体现：
- 候选筛选
- 候选摘要
- 转化建议
- 用户动作
- 与 Note / Idea 的来源关联

### 7.3 Task

Task 统一承接两类来源：
- `SYSTEM`
- `USER`

Trend 生成的 follow-up task 仍必须进入统一 Task Domain，不要新造独立待办系统。

### 7.4 ChangeProposal

Trend 场景下若外部内容要影响内部解释层，必须通过：
- `EVIDENCE` block，或
- `ChangeProposal`

禁止直接静默覆盖 `notes.current_*`。

### 7.5 Search / External Evidence 继承规则

即使进入 Trend 阶段，也不能打破既有外部证据治理边界：
- 外部结果可以形成 evidence block
- 外部结果可以生成 proposal
- 外部结果不能直接覆盖内部知识当前解释层

### 7.6 CaptureJob

V1 仍只支持：
- `TEXT`
- `URL`

Trend 阶段不应为了抓趋势而扩大用户 Capture 输入边界；Trend 来源接入应走 Trend Source / Connector，而不是篡改 Capture 语义。

---

## 8. 架构与分层要求

### 8.1 后端分层

后端默认采用如下边界：

1. API / Controller 层  
   负责 DTO、参数校验、响应 envelope、错误码映射。

2. Application / Orchestration 层  
   负责编排 Trend ingest、Trend analyze、Trend convert、写 Trace、组织规则决策。

3. Domain 层  
   负责 Trend、Note、Idea、Task、Proposal 等核心业务规则与状态机。

4. Persistence 层  
   负责 Repository、ORM 映射、查询与迁移。

不要把“主编排 Agent”写成一个无边界的大类。  
**Orchestrator 只负责路由、状态推进、治理与 trace。具体处理放到 Domain Service / Worker Agent。**

### 8.2 Worker Agent 边界

当前 Phase 4 只允许最小 Worker Agent 扩展点，不要把未来 Phase 5/6 的完整逻辑提前实现。

最小边界：
- Trend Source Connector / Provider
- Trend Normalizer
- Trend Agent（摘要 / 标签 / 转化建议）
- Trend Conversion Service
- 必要时复用现有 Idea Agent / Note Service

---

## 9. Phase 4 特殊规则

### 9.1 默认 Trend Plan

Phase 4 允许并鼓励提供默认内置 Trend plan，但必须满足：
- 默认源有限且可解释
- 默认计划可显式查看或可在代码 / 文档中明确说明
- 默认行为是“进入 Inbox + 给出建议”，而不是静默自动转化

推荐默认计划：
- `HN` + `GITHUB`
- 固定抓取窗口
- 固定关键词偏置
- 固定 top-N 限制

### 9.2 Trend AI 介入边界

Trend AI 负责：
- 结构化分析
- 简练摘要
- why-it-matters
- topic tags
- `note_worthy` / `idea_worthy`
- `suggested_action`

Trend AI 不负责：
- 静默批量创建 Note
- 静默批量创建 Idea
- 直接覆盖内部知识主体
- 越过治理链路写多个核心对象

### 9.3 Trend -> Idea 复用规则

Trend 提升为 Idea 后，应优先复用已有 Idea 生命周期与 assess 逻辑。  
禁止在 Trend 侧重新发明一套与 Idea assess 重复的独立创意分析系统。

### 9.4 Today / Workspace 影响边界

Trend 阶段可以新增 Trend Inbox，但不要为了它破坏已有 Today / Review / Search / Idea 主路径。  
若 Trend 转化生成任务，这些任务进入统一 Task / Workspace，而不是新造趋势专属任务面板。

---

## 10. 何时必须同步文档

出现以下任一情况，执行结束前必须更新文档：

- 新增或修改 Trend 相关表、字段、索引
- 新增或修改 Trend API 路径或字段
- Trend 来源语义、候选语义、转化语义发生变化
- 默认 Trend plan 或默认来源集合发生变化
- 当前已完成子步骤变更
- 已知风险新增或消除
- 新增 deferred item 或移除 deferred item

最少更新：
- `docs/codex/Documentation.md`

必要时再更新：
- README
- schema / API 补充说明
- `docs/codex/Plan.md`

---

## 11. 验证策略

### 11.1 默认验证顺序

1. 最窄范围单元测试 / 集成测试
2. 构建 / 类型检查
3. 必要时的数据库迁移验证
4. 必要时的手工链路检查

### 11.2 验证输出要求

必须明确写出：
- 跑了什么
- 结果如何
- 哪些没跑
- 还剩什么风险

禁止用“理论上可以”“按理说没问题”代替验证结果。

---

## 12. 每次输出模板

默认按下面结构向用户汇报：

### 本次完成
- 说明完成的具体小目标

### 修改文件
- 列出关键文件与职责

### 验证结果
- 列出命令 / 测试 / 构建结果

### 日志覆盖
- 列出关键日志点
- 说明哪些链路带 `trace_id`
- 说明失败场景如何定位

### 风险与下一步
- 写清尚未覆盖的边界
- 标出 deferred items
- 给出最合理的下一个子步骤
