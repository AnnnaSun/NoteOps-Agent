# Plan.md

## 1. 当前阶段定义

当前仓库进入 **Phase 2：Review / Search / Today 工作台**。

阶段目标：在 Phase 1 最小闭环基础上，交付一个可展示的“知识到行动”增强版本，让用户能够：

- 在 Today 页面看到需要处理的 Review 和 Task
- 在 Review 中区分 schedule / recall，并记录完成质量
- 在 Search 中同时看到内部命中、内部关联和外部补充
- 在 Proposal / Event / Trace 中看到关键自动化治理链路

原始路线图将 Phase 2 定义为 `Review / Search`，Phase 3 才是 `Idea`；本计划按此主线执行。fileciteturn1file0

---

## 2. 本阶段验收标准

满足以下条件，才能认为 Phase 2 达到可 review 状态：

### 2.1 Review

- `review_states` 与相关领域逻辑支持双池：`SCHEDULE / RECALL`
- Review 完成接口支持：
  - `completion_status`
  - `completion_reason`
  - `self_recall_result`
  - `note`
- Review 结束后能根据结果更新：
  - `next_review_at`
  - `next_queue_type`
  - `retry_after_hours`
  - 必要时生成 follow-up task

### 2.2 Search

- Search 接口返回三分栏：
  - `exact_matches`
  - `related_matches`
  - `external_supplements`
- `related_matches` 带 `relation_reason`
- `external_supplements` 带来源、关键词、关系标签
- 外部结果不会直接改写当前 Note 正文/摘要

### 2.3 Today / Calendar

- Today 页面按区块展示：
  - Today Reviews
  - Today Tasks
- Upcoming / Calendar 列表能展示到期 Task 与待处理 Review
- Task 返回必须带 `task_source`
- User Task 支持创建、完成、跳过、按 due_at 基础排序

### 2.4 Proposal / Trace / Event

- proposal apply / rollback 返回回退信息
- Search / Review 相关关键动作可写入 `user_action_events`
- agent_traces / tool_invocation_logs 至少覆盖关键链路

### 2.5 文档

- `docs/codex/Prompt.md`
- `docs/codex/Plan.md`
- `docs/codex/Documentation.md`
- `AGENTS.md`

必须与实际实现状态一致。

---

## 3. Phase 2 子步骤拆分

以下按最小可验收切片推进。每次只做一个子步骤，不自动跨到下一个。

### Step 2.1：Phase 2 基线对齐

#### 目标

把仓库的阶段基线从 Phase 1 更新为 Phase 2，避免 agent 继续被旧边界约束。

#### 必做

- 更新 `docs/codex/Prompt.md`
- 更新 `docs/codex/Plan.md`
- 建立 / 更新 `docs/codex/Documentation.md` 的 Phase 2 概览
- 更新 `AGENTS.md` 中“当前阶段边界”与“Phase 2 允许/禁止项”
- 视当前内容更新 `Implement.md`

#### 验收

- 新文档明确写清本阶段目标、范围、Deferred Backlog
- 仓库不再出现“当前唯一目标仍是 Phase 1”的硬冲突

---

### Step 2.2：Review Schema / Contract 对齐

#### 目标

把 Review 从最小闭环升级为双池 + 完成语义。

#### 必做

- 对齐 `review_states` 的字段与枚举：
  - `queue_type`
  - `completion_status`
  - `completion_reason`
  - `unfinished_count`
  - `retry_after_hours`
- 对齐 Review complete API 请求体/响应体
- 对齐 DTO、entity、service、controller、测试

#### 验收

- 后端可正确处理：COMPLETED / PARTIAL / NOT_STARTED / ABANDONED
- 根据不同状态写回下一队列与重试信息
- Today 查询可消费这些字段

---

### Step 2.3：Today / Upcoming 查询聚合

#### 目标

建立真正的工作台查询，而不只是零散接口。

#### 必做

- 新增或完善 Today 聚合查询
- 新增或完善 Upcoming / Calendar 列表查询
- 聚合 Review 与 Task，前端分区展示
- Task 保留 `task_source`

#### 验收

- 能取回 Today reviews
- 能取回 Today tasks
- 能取回 upcoming items
- 前端或接口层已按类型区分展示

---

### Step 2.4：User Task 扩展

#### 目标

把 User Task 从 Today 最小能力扩到带 due_at 的工作台能力。

#### 必做

- 任务创建支持 `due_at`
- Today / Upcoming 使用 `due_at` 排序
- 支持 complete / skip
- 支持 `NOTE / IDEA / REVIEW / NONE` 绑定

#### 验收

- 可创建用户任务并在 Today/Upcoming 中看到
- 可完成或跳过任务
- 状态变更可回写事件

---

### Step 2.5：Search 三分栏后端契约

#### 目标

把 Search 做成真正的 Phase 2 交付，而不是简单模糊查询。

#### 必做

- 实现 `exact_matches`
- 实现 `related_matches`
- 实现 `external_supplements`
- `related_matches` 输出 `relation_reason`
- `external_supplements` 输出关系标签
- 外部结果只生成 evidence/proposal，不直接落地正文

#### 验收

- Search API 返回三分栏结构
- 内部命中、内部关联、外部补充至少有最小可运行实现
- 文档与 DTO 保持一致

---

### Step 2.6：Proposal / Event / Trace 补强

#### 目标

让 Review 与 Search 的关键链路具备治理记录，不再只是功能行为。

#### 必做

- Proposal apply / rollback 响应补充：
  - `rollback_token`
  - 快照摘要
- Search 保存 evidence / 生成 proposal 时写事件
- Review 完成、部分完成、未开始等动作写事件
- 对关键链路补充 trace / tool log

#### 验收

- 关键动作可审计
- 行为与事件字典一致
- 文档同步更新

---

### Step 2.7：Web 工作台接入

#### 目标

把 Phase 2 后端能力接到真实页面。

#### 必做

- Today 页面分区展示 Review / Task
- Search 页面展示三分栏
- Review 完成表单支持：
  - completion_status
  - completion_reason
  - self_recall_result
  - note
- Upcoming 页面展示基础列表

#### 验收

- 页面连真实接口
- 加载 / 空态 / 错误态完整
- 至少完成一轮手工链路自检

---

### Step 2.8：阶段文档收口

#### 目标

确认 Phase 2 最小闭环已经形成，并标注仍然 deferred 的内容。

#### 必做

- 更新 `Documentation.md`
- 更新必要的 schema / API 补充文档
- 标记哪些已完成，哪些仍 deferred

#### 验收

- 文档描述与仓库代码一致
- Deferred Backlog 没有丢失

---

## 4. 子步骤之间的依赖关系

严格依赖如下：

- 2.1 → 2.2 → 2.3 → 2.4 → 2.5 → 2.6 → 2.7 → 2.8

说明：

- 没有 Step 2.2，就不要开始 Today 聚合，因为 Review 契约会漂移
- 没有 Step 2.3 / 2.4，就不要宣称 Today / Calendar 完成
- 没有 Step 2.5，就不要做 Search 页面终态
- 没有 Step 2.6，就不要宣称治理链路完整

---

## 5. 当前阶段 Deferred Backlog

以下功能是为了完成 Phase 2 最小闭环而明确延后，后续必须补回：

1. 正式 Idea 生命周期闭环
2. 正式 UserPreferenceProfile 重算与应用
3. Trend 正式闭环
4. 更真实的 external search provider 接入
5. 周/月 Calendar 视图
6. User Task 编辑 / 重排 / 批量能力
7. recall question 与 recall scoring 增强
8. tag_definitions 与标签治理
9. PWA 离线 review 与 sync 完整链路
10. 更强的 proposal 审计与治理体验

---

## 6. 每次执行时的固定要求

执行任何 Step 2.x 前，必须先读：

1. `AGENTS.md`
2. `docs/codex/Prompt.md`
3. `docs/codex/Plan.md`
4. `docs/codex/Implement.md`
5. `docs/codex/Documentation.md`

执行中必须遵守：

- 只实现当前子步骤最小闭环
- 不顺手开启下一步
- 变更 schema 时同步检查 DTO / service / frontend / docs
- 不得谎报验证结果

