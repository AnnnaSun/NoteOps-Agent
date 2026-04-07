# Schema / API Drift Report

更新时间：2026-04-03

本报告对照的主要文档基线：

- `AGENTS.md`
- `docs/codex/Prompt.md`
- `docs/codex/Plan.md`
- `docs/codex/Implement.md`
- `docs/codex/Documentation.md`
- `docs/history/Documentation.md`

结论先行：

- 当前代码现实明显更接近“Phase 2.5~2.8 加上 Capture/Search/Review AI 补丁”。
- 当前文档基线却把仓库描述成“Phase 3：Idea 正式闭环”。
- 最大漂移不是字段小差异，而是整条 Idea 领域线在文档中被写成已进入主实现基线，但代码里完全不存在。

## 1. 一级漂移：文档写了，代码没有

## 1.1 Idea 全链路缺失

`docs/codex/Prompt.md`、`docs/codex/Plan.md`、`docs/codex/Documentation.md` 都把以下能力写成当前 Phase 3 主线或基线：

- `ideas` 表 / schema
- Idea 状态机
- Idea 创建
- Note -> Idea 派生
- Idea Assess
- Idea -> Task
- Idea List / Detail / Assess / Generate Task UI
- Idea 相关 trace / event / log

代码现实：

- 没有 `ideas` migration
- 没有 `idea` package
- 没有 `IdeaController`
- 没有 `IdeaApplicationService`
- 没有 `IdeaRepository`
- 没有 `Idea DTO`
- 没有前端 Idea 页面与 API client
- 没有任何 `IDEA_*` user event

这不是“部分未完成”，而是“整条领域线尚未落地”。

## 1.2 文档中的 Idea API 全部不存在

`docs/codex/Documentation.md` 中列出的以下 API 当前都不存在：

- `POST /api/ideas`
- `POST /api/notes/{noteId}/ideas`
- `GET /api/ideas`
- `GET /api/ideas/{id}`
- `POST /api/ideas/{id}/assess`
- `POST /api/ideas/{id}/generate-task`
- `POST /api/ideas/{id}/start`
- `POST /api/ideas/{id}/archive`
- `POST /api/ideas/{id}/reopen`

## 1.3 文档中的 Idea 工作台全部不存在

`docs/codex/Documentation.md` 的前端基线声明：

- Idea List
- Idea Detail
- Assess 入口
- Generate Task 入口

当前前端现实：

- 单页只包含 Capture、Search、Note、Proposal、Workspace、Review
- 不存在 Idea 区块
- 不存在任何 Idea 文案、状态、按钮、client 函数、类型定义

## 2. 二级漂移：代码有，文档写法不准确或不完整

## 2.1 Task 关联字段名漂移

文档多处写的是：

- `linked_entity_type`
- `linked_entity_id`

代码和数据库真实字段是：

- `related_entity_type`
- `related_entity_id`

这会直接影响：

- schema 对照
- API contract
- 前后端字段对齐

## 2.2 ChangeProposal 初始状态漂移

前端和文档部分语义仍兼容 `PENDING`。

真实代码和 schema 是：

- `PENDING_REVIEW`

前端之所以还能兼容，是因为 `canApplyProposal()` 同时接受 `PENDING` 和 `PENDING_REVIEW`，这说明调用方已经在容忍文档/历史契约漂移。

## 2.3 Review 自评字段落库方式与直觉文档不一致

文档容易让人理解为 `self_recall_result` 与 `note` 是 `review_states` 的显式字段。

真实 schema 不是：

- 两者保存在 `review_meta jsonb`
- service 层再把它们还原为 `ReviewStateView.selfRecallResult` 与 `ReviewStateView.note`

这是实际 schema 语义，文档没有写清。

## 2.4 Note API 只暴露部分字段

`notes` 表真实有：

- `note_type`
- `status`
- `current_tags`
- `current_topic_labels`
- `current_relation_summary`
- `importance_score`
- `version`
- `extra_attributes`

但当前 Note API 只暴露：

- `title`
- `current_summary`
- `current_key_points`
- `latest_content_id`
- latest raw/clean text
- evidence blocks

也就是说，代码里的“真实 schema”与“对外 contract”之间有明显裁剪层，文档目前没有把这层说清楚。

## 2.5 Search 外部补充的“真实程度”被文档高估

`docs/codex/Documentation.md` 已说明“当前不引入真实外部 provider 抓取”，这点是对的。

但如果只看 UI/文档叙述，容易误以为 external supplement 至少来自某种真实外部检索。

代码现实更保守：

- `buildExternalSupplementSeeds(query)` 直接构造两条 stub：
  - `Search Stub Background`
  - `Search Stub Follow-up`
- `source_uri` 也是 `stub://search/...`
- AI 只增强这些 stub 的解释字段

所以这里不是“外部检索弱化版”，而是“外部补充展示位 + AI 标签增强”。

## 2.6 Search evidence / proposal 的治理边界更严格

代码里明确实现了：

- fallback external supplement 可以保存为 `EVIDENCE`
- `is_ai_enhanced=false` 时禁止生成 proposal

前端和服务都执行了这个约束。文档有描述，但不够突出，容易被当成 UI 限制，而不是治理边界。

## 2.7 Proposal 生成并不使用 AI

当前 proposal 生成路径：

- `ChangeProposalApplicationService.generate()`
- `SearchGovernanceApplicationService.generateProposal()`

都基于：

- `NoteInterpretationSupport.summarize()`
- `extractKeyPoints()`
- 拼接式规则逻辑

不是 AI 生成。文档里如果只写“生成建议”，容易被误读为 AI proposal。

## 2.8 前端存在 API client，但页面没有用

`web/src/api.ts` 里有：

- `listReviewsToday`
- `listTasksToday`

但 `App.tsx` 实际使用的是：

- `getWorkspaceToday`
- `getWorkspaceUpcoming`

这说明“前端存在接口函数”不等于“页面能力已经接通”。

## 3. 三级漂移：代码实现超出了旧 Phase 2 文档

相对 `docs/history/Documentation.md` 而言，当前代码已经超出原始 Phase 2 盘点，主要体现在：

- Capture AI 最小闭环已经存在
- Search AI enhancement 已存在
- Review AI render / feedback 已存在

也就是说，仓库现实不是静止在旧的 Phase 2 文档上，但又没有真正进入 Idea Phase 3。

## 4. Schema 层面的现实风险

以下不是“文档漂移”而已，而是实际 schema 边界的可控性问题：

## 4.1 只有一份 `V1` migration

当前所有核心表、AI 补丁、Phase 2/3 演进都还挤在：

- `V1__create_phase1_core_tables.sql`

现实含义：

- schema 历史不可分段回看
- 很难从 migration 层判断哪个能力属于哪个阶段
- 文档容易先行，migration 却没有阶段切分证据

## 4.2 多个关键字段无数据库枚举约束

库里缺少 check 的关键点包括：

- `capture_jobs.status`
- `notes.note_type`
- `notes.status`
- `agent_traces.status`
- `tasks.related_entity_type`

尤其 `tasks.related_entity_type` 的代码枚举是 `NOTE / IDEA / REVIEW / NONE`，但数据库不校验，容易出现脏值。

## 4.3 `TaskRelatedEntityType.IDEA` 只是预留，不是已实现

当前 `TaskRelatedEntityType` 确实包含 `IDEA`，但这只是类型层预留：

- 没有 Idea 实体
- 没有 Idea task 派生路径
- 没有任何 controller/service 在真实使用 `IDEA`

如果文档把这一点写成“已有 Idea->Task”，会误导判断。

## 5. API Contract 漂移点

## 5.1 Capture API 与表字段存在双命名

外部 canonical 请求字段：

- `source_type`
- `raw_text`
- `source_url`

数据库内部字段：

- `input_type`
- `raw_input`
- `source_uri`

后端通过 `@JsonAlias` 做兼容。这是现实存在的双命名层。

## 5.2 Review 完成接口比旧文档更复杂

真实接口已支持：

- `completion_status`
- `completion_reason`
- `self_recall_result`
- `note`
- AI feedback 四字段响应

这比早期“只完成一次 review”复杂得多，文档应该把 queue-specific 约束写得更显式：

- `RECALL` 必须提供 `self_recall_result`
- `SCHEDULE` 禁止提交 `self_recall_result` 和 `note`

## 5.3 Workspace 才是当前前端主 contract

前端真实主查询不是：

- `GET /api/v1/reviews/today`
- `GET /api/v1/tasks/today`

而是：

- `GET /api/v1/workspace/today`
- `GET /api/v1/workspace/upcoming`

所以如果后续文档仍把页面理解为多个独立接口拼接，会偏离现实实现。

## 6. 文档缺口

仓库里没有发现明确独立存在的：

- 最终 PRD 文件
- 独立 ERD 冻结版
- 独立 API 冻结版
- 独立状态机 JSON 示例集

当前最接近这些职责的文档，其实是：

- `docs/codex/Documentation.md`
- `docs/history/Documentation.md`
- `docs/codex/Plan.md`
- `docs/history/Plan.md`

这意味着当前“文档对照物”本身就混合了 roadmap、契约和阶段说明，容易把“目标状态”写成“实现现实”。

## 7. 建议优先修正文档的点

只给建议，不改代码：

1. 先把 `docs/codex/Documentation.md` 从“Phase 3 Idea 已进入现实基线”回收为“当前真实落地能力 + Idea 未实现说明”。
2. 单独补一份真实 API / schema inventory，避免继续把 roadmap 写进实现基线。
3. 明确 `related_entity_type` / `related_entity_id` 才是当前 Task contract。
4. 明确 `PENDING_REVIEW` 才是当前 ChangeProposal 初始状态。
5. 明确 Search external supplement 当前是 stub seed，不是外部 provider。
6. 明确 Review 的 `self_recall_result` / `note` 实际存于 `review_meta`。
7. 如果继续推进阶段开发，后续 migration 应拆分，不要继续把所有现实塞在 `V1` 里。

