# Schema / API Drift Report

更新时间：2026-04-09

本报告对照的主要文档基线：

- `AGENTS.md`
- `docs/codex/Prompt.md`
- `docs/codex/Plan.md`
- `docs/codex/Implement.md`
- `docs/codex/Documentation.md`
- `docs/history/Documentation.md`

结论先行：

- 当前代码现实已经不再是“只有 Phase 2.5~2.8 补丁”，而是进入了 Phase 3 的最小 Idea 主线。
- 当前仓库已真实落地：
  - `ideas` migration
  - Idea create / assess / generate-task
  - `GET /api/v1/ideas`
  - `GET /api/v1/ideas/{id}`
  - Web 单页 Idea Workspace
- 当前仍存在的主要漂移，不再是“Idea 完全不存在”，而是“文档容易把最小闭环误读成完整 Phase 3 生命周期已收口”。

## 1. 一级漂移：文档目标仍大于当前现实

## 1.1 Idea 已落地最小主线，但不是完整生命周期闭环

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

- 已有 `V2__create_ideas_table.sql`
- 已有 `V3__rename_idea_source_mode_to_manual.sql`
- 已有 `IdeaController`、`IdeaApplicationService`、`IdeaAssessmentService`、`IdeaTaskGenerationService`、`IdeaQueryService`
- 已有 `IdeaRepository` / `JdbcIdeaRepository`
- 已有 `dto/idea`
- 已有 Web 单页内的 Idea panel 和对应 API client / types
- 已有 `IDEA_*` 相关 user event / trace / log
- Idea source mode 已从旧实现中的 `INDEPENDENT` 统一收敛到 `MANUAL`

当前真正未完成的是：

- Promote to Plan / Archive / Reopen
- Idea Create 的 Web 表单入口
- 完整生命周期收口后的进一步治理补强

## 1.2 文档中的部分 Idea API 已存在，部分仍不存在

当前已存在：

- `POST /api/v1/ideas`
- `GET /api/v1/ideas`
- `GET /api/v1/ideas/{id}`
- `POST /api/v1/ideas/{id}/assess`
- `POST /api/v1/ideas/{id}/generate-task`

当前仍不存在：

- `POST /api/v1/notes/{noteId}/ideas`
- `POST /api/v1/ideas/{id}/start`
- `POST /api/v1/ideas/{id}/archive`
- `POST /api/v1/ideas/{id}/reopen`

## 1.3 文档中的 Idea 工作台已最小落地，但不是完整工作台

`docs/codex/Documentation.md` 的前端基线声明：

- Idea List
- Idea Detail
- Assess 入口
- Generate Task 入口

当前前端现实：

- 单页仍然只有一个 `App.tsx`，但已经新增 Idea 区块
- 已存在：
  - Idea List
  - Idea Detail
  - Assess 按钮
  - Assessment Result 展示
  - Generate Tasks / View Tasks 按钮
  - Idea 首屏请求失败只留在 Idea 面板，不再把 Note / Workspace 一起打成错误态
  - Idea action 成功后的刷新失败会在 Idea 面板内提示，不再误报成 assess / generate 动作失败
- 仍不存在：
  - Create UI
  - Promote / Archive / Reopen
  - Kanban / Pipeline / 高级筛选

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

也就是说，仓库现实不是静止在旧的 Phase 2 文档上，而是已经进入 Idea Phase 3 的最小主线；当前问题是完整生命周期还没有收口。

## 4. Schema 层面的现实风险

以下不是“文档漂移”而已，而是实际 schema 边界的可控性问题：

## 4.1 migration 已拆出 `V2`，但后续阶段切分仍要继续保持

当前 migration 现实是：

- `V1__create_phase1_core_tables.sql`
- `V2__create_ideas_table.sql`
- `V3__rename_idea_source_mode_to_manual.sql`

现实含义：

- Phase 3 的最小 Idea schema 已经有独立 migration 证据
- `MANUAL` source mode 的持久化合同也已经有独立迁移证据
- 但后续如果继续补 lifecycle action、治理补强或 schema 演进，仍应继续按阶段拆分，而不是重新把新现实塞回旧 migration 叙述里

## 4.2 多个关键字段无数据库枚举约束

库里缺少 check 的关键点包括：

- `capture_jobs.status`
- `notes.note_type`
- `notes.status`
- `agent_traces.status`
- `tasks.related_entity_type`

尤其 `tasks.related_entity_type` 的代码枚举是 `NOTE / IDEA / REVIEW / NONE`，但数据库不校验，容易出现脏值。

## 4.3 `TaskRelatedEntityType.IDEA` 已被最小主链路真实使用

当前 `TaskRelatedEntityType.IDEA` 不再只是预留：

- `IdeaTaskGenerationService` 会真实创建 `related_entity_type=IDEA`
- Web 侧 `View Tasks` 已按 `related_entity_type === "IDEA"` 过滤展示

当前仍需注意：

- 这只能说明最小 Idea -> Task 主链路已落地
- 不能把它误读成完整 Idea 生命周期已经全部实现

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

1. 保持 `docs/codex/Documentation.md` 对“Phase 3 最小主线已落地”的表述，同时明确完整生命周期尚未收口，不要再回退成“Idea 未实现”。
2. 单独补一份真实 API / schema inventory，避免继续把 roadmap 写进实现基线。
3. 明确 `related_entity_type` / `related_entity_id` 才是当前 Task contract。
4. 明确 `PENDING_REVIEW` 才是当前 ChangeProposal 初始状态。
5. 明确 Search external supplement 当前是 stub seed，不是外部 provider。
6. 明确 Review 的 `self_recall_result` / `note` 实际存于 `review_meta`。
7. 如果继续推进阶段开发，后续 migration 应继续按阶段拆分，延续 `V2` 之后的分段证据。
