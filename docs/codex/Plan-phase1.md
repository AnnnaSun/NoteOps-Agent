# Plan
# docs/codex/Plan.md

## 1. 计划用途

本文件用于把 Phase 1 拆成可执行、可验收、可回退的最小里程碑。  
Codex 处理任务时，应优先完成“一个完整小闭环”，而不是在多个模块上同时浅挖。

---

## 2. Phase 1 总目标

交付一个可运行的 NoteOps 知识内核，覆盖：

- Capture（TEXT / URL）
- Note / NoteContent
- ReviewState
- Task（SYSTEM / USER）
- ChangeProposal
- AgentTrace / ToolInvocationLog
- UserActionEvent
- PostgreSQL 主库
- 最小 Web 展示或最小 API 可用性

---

## 3. 执行原则

1. 先服务端，后前端
2. 先数据与状态机，后页面细节
3. 先主链路闭环，后体验优化
4. 先可验证，再谈扩展
5. 一个里程碑只解决一个主问题

---

## 4. Phase 1 里程碑拆分

### M1. 仓库骨架与工程基线

#### 目标
建立后端、前端、文档的最小工程骨架。

#### 最小交付
- `server/` 初始化
- `web/` 初始化
- 基础 README / 启动说明
- `docs/codex/` 建立并接入当前文档
- 后端应用可启动
- 前端应用可启动（哪怕只有空壳页面）

#### 验收点
- 后端构建通过
- 前端构建通过
- 仓库目录清晰、无混乱生成物

#### 不在本里程碑做
- 具体业务逻辑
- 复杂页面
- 数据库建模细节

---

### M2. PostgreSQL 核心模型与迁移

#### 目标
落地 Phase 1 的核心表结构与最小索引。

#### 最小交付
创建并迁移以下表：
- `notes`
- `note_contents`
- `review_states`
- `tasks`
- `change_proposals`
- `capture_jobs`
- `agent_traces`
- `tool_invocation_logs`
- `user_action_events`

#### 关键建模要求
- 所有核心表带 `user_id`
- `notes` 与 `note_contents` 分层
- `review_states` 支持双池和完成状态
- `tasks` 支持 SYSTEM / USER
- `change_proposals` 带 `target_layer`

#### 验收点
- 迁移可执行
- 表结构与冻结文档一致
- 关键索引具备
- 主外键和枚举语义清楚

#### 不在本里程碑做
- Phase 2 的完整 `ideas`
- Phase 3 的完整 `trend_items`
- 高级全文检索方案

---

### M3. Capture → Note 主链路

#### 目标
打通从输入到 Note 落库的第一条主链路。

#### 最小交付
- `POST /api/v1/captures`
- `GET /api/v1/captures/{id}`
- CaptureJob 状态推进
- TEXT 输入转 Note
- URL 输入保留最小兼容流程（允许先做占位提取/模拟提取，但状态机与存储结构必须真实）

#### 数据结果要求
- 创建 `capture_jobs`
- 写入 `agent_traces`
- 写入 `tool_invocation_logs`（哪怕当前只有占位工具）
- 落 `notes`
- 落 `note_contents`
- 记录最小 `user_action_events`

#### 验收点
- 可以提交 TEXT capture 并创建新 Note
- Note 可回查
- Trace 可关联到 capture
- 失败分支有错误码/错误消息

#### 不在本里程碑做
- 智能合并优化到很复杂
- 外部证据增强
- 完整 URL 抽取质量优化

---

### M4. Review 基础闭环

#### 目标
实现 Today Review 的最小可用闭环。

#### 最小交付
- `GET /api/v1/reviews/today`
- `POST /api/v1/reviews/{review_item_id}/complete`
- `review_states` 读写
- `completion_status` / `completion_reason`
- `queue_type = SCHEDULE | RECALL`

#### 调度要求
至少体现：
- 已完成且掌握良好：留在正常调度路径
- 已接触但不稳：进入 recall
- 未开始 / 放弃：更新 unfinished 语义与后续优先级

#### 验收点
- Today 列表可返回
- 完成 Review 后状态被更新
- 响应返回 `next_review_at` 或短期重试信息
- 有基本 trace / event 记录

#### 不在本里程碑做
- 复杂遗忘曲线算法
- 多轮智能题目生成
- 花哨的复习 UI

---

### M5. Task 基础闭环

#### 目标
让任务系统不再只是一张表，而是具备最小使用价值。

#### 最小交付
- `POST /api/v1/tasks`
- `GET /api/v1/tasks/today`
- `POST /api/v1/tasks/{task_id}/complete`
- `POST /api/v1/tasks/{task_id}/skip`

#### 关键要求
- 同时支持 `task_source = SYSTEM | USER`
- 同时支持绑定对象与独立任务
- Today 返回中必须包含 `task_source`

#### 验收点
- 用户可创建 User Task
- Review/Proposal 可派生 System Task（可先实现一种）
- 完成/跳过能更新状态并记事件

#### 不在本里程碑做
- Calendar 全功能
- 复杂优先级引擎
- 子任务系统

---

### M6. Proposal / Trace / Event 治理闭环

#### 目标
把“系统建议”与“系统做了什么”变成可见、可追踪的正式机制。

#### 最小交付
- ChangeProposal 生成与查询能力
- Apply / rollback 最小 API
- `before_snapshot` / `after_snapshot` / `diff_summary`
- 回退凭证 `rollback_token`
- Trace / ToolLog / UserActionEvent 关联写入

#### 验收点
- 至少有一个低风险 proposal 可应用
- rollback 流程可走通
- proposal 不直接改原始正文
- trace / event 有可追踪的链路

#### 不在本里程碑做
- 复杂风险规则引擎
- 多 proposal 合并器
- 高级 diff UI

---

### M7. 最小 Web 接入

#### 目标
给 Phase 1 后端主链路提供最小 Web 骨架，避免只剩裸 API。

#### 最小交付
- Capture 提交页
- Note 列表/详情页（最小版）
- Today 视图（Review + Task）
- Proposal 基础展示位

#### 验收点
- Web 可跑通至少一条主链路：Capture → Note 查看
- Today 页能看到 Review/Task 的最小结果
- 不要求完整产品级交互

#### 不在本里程碑做
- 复杂设计系统
- 响应式细节打磨
- 完整 PWA

---

### M8. 文档对齐与收口

#### 目标
让代码、表结构、状态机、接口和 Codex 文档保持一致。

#### 最小交付
- README 更新
- 必要 API / schema 文档补充
- `docs/codex/Documentation.md` 更新
- Phase 1 已完成/未完成项清单

#### 验收点
- 文档与实际实现无明显冲突
- 明确已完成范围和未覆盖风险

---

## 5. 推荐执行顺序

默认按以下顺序推进：

1. M1 仓库骨架
2. M2 数据库与迁移
3. M3 Capture → Note
4. M4 Review
5. M5 Task
6. M6 Proposal / Trace / Event
7. M7 Web 最小接入
8. M8 文档收口

如果用户明确指定从某个里程碑开始，则优先服从用户要求，但不得跳过该里程碑依赖的基础能力。

---

## 6. 当前建议的“下一步”判断规则

Codex 在没有额外说明时，默认寻找 **尚未完成的最小前置里程碑**，只做下一个最合理切片：

- 没有工程骨架：先做 M1
- 没有迁移：先做 M2
- 没有 Capture 主链路：先做 M3
- Note 已落地但 Review 没闭环：做 M4
- Review 有了但用户任务缺失：做 M5
- 治理链路缺失：做 M6
- API 跑通但没有最小 UI：做 M7
- 功能有了但文档漂移：做 M8

---

## 7. Definition of Done（按里程碑）

每个里程碑完成，至少满足：

1. 目标范围内的代码已落地
2. 最小验证已执行
3. 没有把未来阶段内容偷塞进当前里程碑
4. 若改动契约/状态/表结构，文档已同步
5. 返回清晰的“下一步建议”
