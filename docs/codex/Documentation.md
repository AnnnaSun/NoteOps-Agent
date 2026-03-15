# Documentation
# docs/codex/Documentation.md

## 1. 文档用途

本文件用于记录当前 NoteOps 仓库在 Codex 视角下的工程状态、已冻结边界、当前阶段目标、已知风险与文档同步说明。

它不是 PRD 的替代品，而是 **“当前仓库应该按什么边界实现”** 的运行记录。

---

## 2. 当前状态快照

### 2.1 当前阶段

- 当前阶段：**Phase 1 重启基线**
- 当前目标：交付可运行的知识内核
- 当前优先：后端主链路优先，Web 最小接入次之
- 当前原则：先可运行、再可治理、后做扩展

### 2.2 当前状态说明

基于现有来源文档，产品、架构、表结构、API、状态机边界已经足够支撑 Phase 1 开发。  
本文件默认假设：**仓库正处于 Phase 1 开发前或开发初期，需要用 Codex 文档防止实现发散。**

如果实际仓库已经有部分代码，则后续应在每个里程碑完成后补写本文件中的“实现进度”和“已验证项”。

---

## 3. 已冻结的核心结论

### 3.1 产品层

1. Note 是第一公民
2. 系统主线是 Knowledge-to-Action
3. 服务端 PostgreSQL 是唯一真相源
4. Web 优先，PWA 和移动端后置
5. 自动化必须具备建议、确认、应用、撤销、追溯链路

### 3.2 架构层

1. 主编排 Agent + Worker Agents
2. Orchestrator 只做路由、状态推进、治理、trace
3. 原始内容与当前解释层分离
4. 核心字段固定，扩展属性 JSONB
5. 外部证据只形成 evidence / proposal / conflict，不直接覆盖内部知识

### 3.3 Phase 1 关键补丁

1. 所有核心表保留 `user_id`
2. Review 采用双池机制
3. Review 支持 completion_status / completion_reason
4. Task 同时支持 System Task 与 User Task
5. 原始正文只追加，不覆盖
6. Proposal 作用于解释层/元数据层/关系层
7. 离线动作仅限 review、轻量备注、tag 修改

---

## 4. Phase 1 目标对象

### 4.1 Phase 1 应落地的核心表

- `notes`
- `note_contents`
- `review_states`
- `tasks`
- `change_proposals`
- `capture_jobs`
- `agent_traces`
- `tool_invocation_logs`
- `user_action_events`

### 4.2 Phase 1 应优先跑通的主链路

1. Capture(TEXT/URL) → CaptureJob → Note
2. Note 详情查询
3. Today Review → complete → ReviewState 更新
4. User Task 创建 / Today 查询 / 完成 / 跳过
5. Proposal apply / rollback 最小治理链路

---

## 5. Deferred / 后置能力

以下能力允许预留，但当前默认不做正式闭环：

### Phase 2 以后
- `user_preference_profiles`
- `ideas`
- `tag_definitions`

### Phase 3 以后
- `trend_items`
- Trend Inbox
- 来源注册与权重系统

### 更后阶段
- PWA 完整离线
- Android / iOS
- transcript-first 多媒体输入
- 导出中心
- 更强外部 evidence 接入

---

## 6. 当前推荐的仓库落地方向

若仓库还是早期状态，建议维持以下结构：

```text
server/
web/
docs/
docs/codex/
```

后端优先沉淀：
- migration
- entity/model
- repository
- service
- controller
- trace/event/proposal 支撑

前端优先沉淀：
- Capture 页面
- Note 列表 / 详情
- Today 页面（Review + Task）

---

## 7. 已知高风险误实现点

以下是当前最容易被错误实现的部分，必须持续检查：

1. 把 proposal 设计成原始正文覆盖器
2. 把 Review 简化成单池 + 单布尔完成状态
3. 只做 System Task，漏掉 User Task
4. 忽略 `user_id`
5. 把外部 evidence 直接写进 `current_summary`
6. 一开始就过早实现 Trend / Idea / Preference Learning 正式闭环
7. 先堆大量页面和组件，主链路却没跑通

---

## 8. 文档维护约定

每完成一个里程碑后，应补充：

1. 已完成里程碑
2. 实际落地文件
3. 已跑验证
4. 未覆盖风险
5. 与冻结文档的差异（如果有）

推荐追加格式：

```md
## 更新记录 YYYY-MM-DD

### 已完成
- ...

### 验证
- ...

### 风险
- ...
```

---

## 9. 当前这套 Codex 文档的作用范围

本目录中的 Codex 文档只解决两件事：

1. 把冻结文档转换成 Codex 可执行约束
2. 让 Phase 1 实现不再因 prompt 漂移而偏离主线

它不替代：
- PRD
- 完整架构文档
- 对外 API 文档
- 数据库正式说明书

---

## 10. 下一个推荐动作

若当前仓库尚未完成骨架，建议从以下顺序启动：

1. M1 仓库骨架
2. M2 PostgreSQL 迁移与核心表
3. M3 Capture → Note 主链路
4. M4 Review
5. M5 Task
6. M6 Proposal / Trace / Event
7. M7 最小 Web 接入
8. M8 文档收口

如果仓库已经有部分代码，则先对照 `Plan.md` 确定当前停留在哪个里程碑，再继续下一个最小闭环。

## 更新记录 2026-03-15

### 已完成
- M3 已完成并收口：`POST /api/v1/captures`、`GET /api/v1/captures/{id}`、`GET /api/v1/notes` 与 `GET /api/v1/notes/{id}` 已落地。
- Capture 现支持 `TEXT` 真实落 Note，以及 `URL` 的占位提取流程；两者都会真实写入 `capture_jobs`、`notes`、`note_contents`、`agent_traces`、`tool_invocation_logs`、`user_action_events`。
- Note 现支持按 `user_id` 回查列表与详情，列表按 `updated_at desc` 返回当前解释层摘要和关键点。
- 所有新增 REST 响应统一返回 envelope：`success`、`trace_id`、`data/error`、`meta.server_time`；无 trace 的响应也显式返回 `trace_id: null`。
- 缺少 `user_id` 等请求参数时，接口会返回 `400` 和统一 envelope 错误结构。
- `NoteContentType` 代码枚举已与 migration 的取值范围对齐。
- M4 已完成最小后端闭环：`GET /api/v1/reviews/today` 和 `POST /api/v1/reviews/{review_item_id}/complete` 已落地。
- Today Review 采用懒创建策略：已有 Note 若缺少 `SCHEDULE` 记录，会在 Today 查询时自动补建。
- `review_states` 采用双记录保留：同一 Note 可分别保留 `SCHEDULE` 和 `RECALL` 两条状态。
- Review Today 只返回 `title`、`current_summary`、`current_key_points` 等当前解释层数据，不返回原始正文。
- M5 已完成最小后端闭环：`POST /api/v1/tasks`、`GET /api/v1/tasks/today`、`POST /api/v1/tasks/{task_id}/complete` 与 `POST /api/v1/tasks/{task_id}/skip` 已落地。
- User Task 现支持独立任务与绑定任务：可绑定 `NOTE / IDEA / REVIEW / NONE`，其中传入 `note_id` 时会默认按 `NOTE` 绑定。
- Task 写操作现通过事务保护：Task 主记录与对应的 `agent_traces`、`user_action_events` 在同一提交内收口，避免“接口失败但任务已落库”的部分成功状态。
- Today Task 当前返回 `TODO / IN_PROGRESS` 且 `due_at` 为空或不晚于“用户当天结束时间”的任务，并显式返回 `task_source`。接口支持可选 `timezone_offset` 参数；未传时默认按 UTC 兼容旧行为。
- Review 现已接入一条最小 System Task 派生规则：未完成良好的 Review 会 upsert `REVIEW_FOLLOW_UP` 任务；后续完成 Recall 时会关闭该跟进任务。

### 验证
- 已运行 `mvn -q test`（`server/`），通过。
- 已补 Task 事务集成测试：使用真实 Spring 事务边界与 JDBC/H2 数据源，验证 `UserActionEventRepository` 抛错时 `tasks` 写入会整体回滚。

### 风险
- URL 抽取仍是 Phase 1 允许的占位实现，未做真实抓取与质量优化。
- 当前仅实现了 Review 派生 System Task，尚未补 Proposal 派生任务。
- 当前已补 Task 事务回滚集成测试，但其余数据库集成测试仍未覆盖。
