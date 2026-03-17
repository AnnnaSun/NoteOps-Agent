# Documentation.md

## 1. 当前仓库状态

当前仓库状态定义为：

- **Phase 1：最小闭环已完成**
- **Phase 2：开始进入 Review / Search / Today 工作台增强阶段**

这表示仓库已经不再是纯骨架或纯占位状态，而是要围绕以下三个产品化增量继续收口：

1. Review 双池与完成质量语义
2. Search 三分栏与 external evidence 治理
3. Today / Calendar 工作台

路线图依据来自 PRD：Phase 2 为 `Review / Search`，Phase 3 才是 `Idea`。fileciteturn1file0

---

## 2. Phase 2 范围冻结

### 2.1 本阶段包含

#### Review

- 双池：`SCHEDULE / RECALL`
- 完成状态：`COMPLETED / PARTIAL / NOT_STARTED / ABANDONED`
- 完成原因：`TIME_LIMIT / TOO_HARD / VAGUE_MEMORY / DEFERRED`
- 用户 recall 自评：`GOOD / VAGUE / FAILED`
- Review 默认展示载体：`current_summary + current_key_points + necessary extensions`

相关冻结依据见补丁文档与表结构。fileciteturn1file4turn1file5turn1file7

#### Search

- 三分栏：`exact_matches / related_matches / external_supplements`
- `related_matches` 要返回 `relation_reason`
- `external_supplements` 要返回来源、摘要、关键词、关系标签
- 外部结果只进入 evidence / proposal / conflict flow

相关冻结依据见 PRD、架构文档与 AGENTS。fileciteturn1file0turn1file3turn1file8

#### Today / Calendar

- Today 同时展示 Review 与 Task
- Task 同时包含 `SYSTEM / USER`
- 返回必须携带 `task_source`
- 本阶段 Calendar 仅做 `Today + Upcoming` 列表，不做周/月视图

#### Proposal / Event / Trace

- Proposal apply / rollback 需要返回回退信息
- Review / Task / proposal / external evidence 的关键动作需要写入事件
- 核心链路需要可追溯 trace

#### Preference 采集层

- 本阶段允许继续补强 `user_action_events`
- 允许为 `user_preference_profiles` 做输入准备
- 不做完整画像计算与自动注入

---

## 3. Phase 2 不包含

以下能力明确不在当前阶段正式闭环内：

### 3.1 Idea 正式生命周期

- `ideas` 表可预留
- 允许保留最小 DTO / service 扩展点
- 不交付正式 Idea Card / assess / lifecycle 页面

### 3.2 Preference Learning 正式闭环

`user_preference_profiles` 中文定义：

> 用户长期偏好画像。

它的职责不是保存一次性的 UI 选项，而是沉淀用户长期行为后形成的结构化偏好，例如：

- `interest_profile`：用户关心什么，例如关注主题、来源偏好、任务偏好
- `output_style_profile`：用户偏好的输出风格，例如标签风格、摘要长度、表达偏好

架构文档还明确了一个更重要的产品原则：

> 系统先学习用户关心什么，再学习用户怎么表达。

这意味着 `user_preference_profiles` 本质上是未来的“偏好记忆层 / 个性化画像层”，而不是简单配置表。fileciteturn1file3turn1file7

本阶段只预留，不做正式重算闭环。

### 3.3 Trend 正式闭环

Trend Inbox、source registry、趋势转 Note/Idea 留到更后阶段。fileciteturn1file0

### 3.4 Calendar 高级视图

本阶段不做周视图、月历视图、拖拽排期。

---

## 4. 当前核心契约

### 4.1 数据层契约

- PostgreSQL 仍是唯一真相源
- 所有核心表继续保留 `user_id`
- `notes` 保存当前解释层
- `note_contents` 保存原始内容、更新块、证据块
- 原始内容只追加，不静默覆盖

### 4.2 Proposal 契约

- 只作用于 `INTERPRETATION / METADATA / RELATION`
- 不直接改写原始正文
- apply / rollback 需要留痕

### 4.3 Review 契约

- 不能退化成单池 + 单布尔完成状态
- Review complete 请求 / 响应需要反映完成质量与后续调度结果

### 4.4 Task 契约

- 同时支持 `SYSTEM / USER`
- 支持 `NOTE / IDEA / REVIEW / NONE`
- 本阶段重点是 Today / Upcoming 工作台消费能力

### 4.5 Search 契约

- 外部补充只做 evidence / proposal / conflict hint
- 不得直接覆盖本地 Note 当前摘要

---

## 5. 推荐的 Phase 2 实现顺序

1. 对齐 Phase 2 文档与仓库级约束
2. 对齐 Review schema / API / 状态机
3. 建立 Today / Upcoming 聚合接口
4. 扩展 User Task 到 Calendar 列表能力
5. 实现 Search 三分栏后端契约
6. 补强 proposal / event / trace
7. 接入 Web 页面
8. 更新文档与阶段状态

---

## 6. Deferred Backlog（后期必须补回）

以下是为了完成 Phase 2 最小闭环而暂时延后的功能，后续必须补回：

1. 正式 Idea 生命周期闭环
2. 正式 UserPreferenceProfile 画像重算与个性化注入
3. Trend 正式闭环
4. 更真实的 external provider 接入
5. 周 / 月 Calendar 视图
6. User Task 编辑、重排、批量能力
7. recall question / recall scoring 增强
8. tag_definitions 与标签治理
9. PWA 离线 review / sync 完整链路
10. 更完整的 Proposal 治理体验

本清单不得因为“当前没做”而删除。

---

## 7. 当前文档使用方式

### Prompt.md

定义当前阶段的执行边界、做与不做、Deferred Backlog、输出要求。

### Plan.md

定义 Phase 2 的子步骤拆分、依赖关系与验收点。

### Documentation.md

记录当前仓库已经进入到什么阶段、当前语义如何冻结、哪些内容仍 deferred。

---

## 8. 与仓库级文件的同步要求

当 Phase 2 继续推进时，以下情况必须同步更新本文档：

- schema 改动
- Review / Task / Search API 字段改动
- 状态机改动
- Today / Upcoming 页面实际行为变化
- Proposal / Event / Trace 语义变化
- 当前完成状态发生变化
