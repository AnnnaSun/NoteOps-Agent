# Prompt.md

## 1. 目标

你当前执行的是 **NoteOps-Agent Phase 2：Review / Search / Today 工作台**。

本阶段目标不是扩张产品边界，而是在 **Phase 1 已完成最小闭环** 的基础上，把以下三条链路做成可展示、可验证、可继续迭代的产品化增量：

1. Review 双池与完成语义闭环
2. Search 三分栏与 external evidence 治理闭环
3. Today / Calendar 工作台对 Review + Task 的统一承载

你必须严格遵守仓库根原则：

- Note 仍然是第一公民
- PostgreSQL 仍然是唯一真相源
- 原始内容只追加，不静默覆盖
- ChangeProposal 只作用于 `INTERPRETATION / METADATA / RELATION`
- 所有核心聚合继续保留 `user_id`
- 不允许为了“顺手”提前把 Phase 3/4/5 做成正式闭环

相关冻结依据来自最终 PRD、架构文档、补丁整合版和 AGENTS 约束。Phase 2 路线图原始定义为 **Review / Search**，Today / Calendar 属于该阶段主要交付；Search 结果形态冻结为 `exact_matches / related_matches / external_supplements` 三分栏；外部结果只进入 evidence/proposal/冲突提示，不直接覆盖本地 Note。fileciteturn1file0 fileciteturn1file3 fileciteturn1file4

---

## 2. 本阶段范围（已确认）

### 2.1 必做范围

本次 Phase 2 只做以下内容：

#### A. Review 升级

把 Phase 1 的最小 Review 闭环，升级为正式的双池与完成语义闭环：

- `queue_type` 支持：`SCHEDULE / RECALL`
- `completion_status` 支持：`COMPLETED / PARTIAL / NOT_STARTED / ABANDONED`
- `completion_reason` 支持：`TIME_LIMIT / TOO_HARD / VAGUE_MEMORY / DEFERRED`
- `self_recall_result` 保留用户自评：`GOOD / VAGUE / FAILED`
- Review 展示对象为：**当前摘要 + 关键点 + 必要延伸**，不是全文直出
- Review 完成后必须能决定：
  - 更新 `next_review_at`
  - 进入 `SCHEDULE_POOL` 或 `RECALL_POOL`
  - 必要时生成 follow-up system task

这些规则已在补丁文档和表结构文档中冻结。fileciteturn1file4turn1file5turn1file6turn1file7

#### B. Search 升级

把 Search 从占位或基础检索，升级为正式三分栏：

- `exact_matches`
- `related_matches`
- `external_supplements`

要求：

- `related_matches` 必须有 `relation_reason`
- `external_supplements` 必须返回来源、摘要、关键词、关系标签
- 外部结果只允许：
  - 形成 `evidence block`
  - 形成 `change proposal`
  - 形成冲突 / 背景补充 / 延伸阅读提示
- 外部结果不得直接覆盖 `notes.current_summary`

该边界在 PRD、架构和 AGENTS 中是一致冻结的。fileciteturn1file0turn1file3turn1file8

#### C. Today / Calendar 工作台

Today / Calendar 本阶段只做 **列表工作台能力**，不做复杂周/月视图。

必须支持：

- Today 页面同时展示 Review 与 Task
- Task 同时覆盖 `SYSTEM` 与 `USER`
- 返回结果必须带 `task_source`
- 页面采用 **分区展示**，不要混成无结构单流
- Calendar 本阶段只做 `Today + Upcoming` 列表能力，不做周视图/月历

这一点与补丁文档对 Task 和 Today 视图要求一致。fileciteturn1file4turn1file5turn1file6

#### D. User Task 扩展到 Calendar 场景

User Task 本阶段能力边界：

- 创建
- 查看 Today
- 查看 Upcoming / Calendar 列表
- 完成
- 跳过
- 按 `due_at` 基础排序

不要求本阶段完成复杂编辑、拖拽重排、批量操作。

#### E. Proposal 治理补强

本阶段的 Proposal 不只是保留表结构，必须补强以下链路：

- Search 产生的 evidence / proposal
- Review 后产生的 proposal 或 follow-up task
- apply / rollback 的响应结构带 `rollback_token` 与快照摘要
- proposal 操作同步写入 `change_proposals / user_action_events / agent_traces`

相关语义在 JSON 补丁、架构补丁和表结构文档中已冻结。fileciteturn1file5turn1file6turn1file7

#### F. Preference 仅做采集层，不做正式画像学习闭环

本阶段允许：

- 继续完善 `user_action_events`
- 为未来 `user_preference_profiles` 输入做准备
- 在接口与服务层预留最小读取/写入边界

本阶段不要求：

- 完整偏好重算任务
- Prompt 自动演化
- 基于画像的大规模排序/生成逻辑

系统对 Preference 的正式原则是：**先学习用户关心什么，再学习用户怎么表达**。fileciteturn1file4turn1file6turn1file7

---

## 3. 本阶段明确不做

### 3.1 不做正式 Idea 生命周期闭环

`ideas` 表和模型可以预留，但不要在本阶段实现以下完整能力：

- Idea Card 正式页面
- `POST /api/v1/ideas/{id}/assess` 的完整产品链路
- Idea → Task 全闭环执行面板
- Idea 状态流正式推进

原因：原始路线图把 **Idea 放在 Phase 3**。尽管完整表结构文档把 `ideas` 列为 Phase 2，但这里按你已确认的策略处理为 **预留，不抢占主线**。fileciteturn1file0turn1file7

### 3.2 不做正式 UserPreferenceProfile 画像计算

`user_preference_profiles` 本阶段只允许预留模型与采集输入，不做正式学习闭环。完整画像学习更适合后续阶段。fileciteturn1file0turn1file7

### 3.3 不做 Trend 正式闭环

不做 Trend Inbox、source registry、趋势转 Note/Idea 正式流程。Trend 仍留在更后阶段。fileciteturn1file0

### 3.4 不做复杂 Calendar 视图

不做：

- 周视图
- 月历视图
- 拖拽排期
- 时间块视图

本阶段 Calendar 只做列表型 `Upcoming` 工作台。

---

## 4. Deferred Backlog（必须保留，后续补回）

以下内容是为了完成 **Phase 2 最小闭环** 而暂时跳过的功能，**后期必须补回，不得永久遗漏**：

1. 正式 Idea 生命周期闭环
2. 正式 UserPreferenceProfile 画像重算与注入
3. Trend 正式闭环
4. 更强的外部检索增强与真实外部源接入
5. 更完整的 Calendar 视图（周 / 月）
6. User Task 编辑、重排、批处理
7. 更成熟的 recall question / recall scoring
8. tag_definitions 与标签规范化治理
9. PWA 离线 review 与 sync 完整链路
10. 更完整的 Proposal 审计与治理体验

每次做 Phase 2 子步骤时，允许显式写“deferred”，但不得删除该清单。

---

## 5. 实施优先级

严格按下面顺序推进，不要跨步大面积发散：

1. Schema / enum / migration 对齐
2. Review 状态机与 command/query 对齐
3. Today / Upcoming 聚合接口
4. Search 三分栏后端契约
5. Proposal / event / trace 补强
6. Web 工作台接入
7. 文档同步与阶段状态收口

---

## 6. 执行约束

### 6.1 修改原则

- 只做当前子步骤最小闭环
- 先读现有代码，再增量修改
- 不做无关重构
- 不静默更名核心字段、接口路径、状态字面量
- 改 schema 时同步检查 entity / DTO / service / frontend / docs

### 6.2 必须保护的冻结语义

- `notes` 保存当前解释层，`note_contents` 保存追加型原始内容与证据块
- Review 不能退化为单池 + 单布尔完成状态
- Task 不能只保留 `SYSTEM`
- Proposal 不能变成正文覆盖器
- 外部证据不能直接覆盖本地知识正文
- 所有核心表继续保留 `user_id`

### 6.3 验证要求

涉及后端变更至少执行：

- 受影响模块测试
- 构建 / 编译
- migration 校验（若涉及 DB）

涉及前端变更至少执行：

- 构建
- 类型检查 / lint（若已配置）
- 关键页面手工自检说明

若未验证，必须明确写未验证项与风险。

---

## 7. 交付输出格式

每个子步骤完成后，默认按以下格式汇报：

1. **本次完成**
2. **修改文件**
3. **验证结果**
4. **未覆盖风险 / 下一步**

不要只说“done”。
