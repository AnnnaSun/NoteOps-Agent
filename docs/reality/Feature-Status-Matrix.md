# Feature Status Matrix

更新时间：2026-04-03

状态定义：

- `implemented`：前后端真实可用
- `partially implemented`：主链路存在，但有明显边界或缺口
- `stub/mock`：有展示位或返回结构，但核心数据来源不真实
- `not implemented`：当前不存在

## 1. 前端页面 / 区块能力矩阵

| 页面/区块 | 真实入口 | 依赖接口 | 状态 | AI | 说明 |
| --- | --- | --- | --- | --- | --- |
| 单页工作台 Shell | `web/src/App.tsx` | 多接口聚合 | implemented | 否 | 无路由，所有能力在一个页面 |
| `user_id` 上下文切换 | 顶部表单 | 所有接口 | implemented | 否 | 值持久化到 `localStorage` |
| Capture TEXT 提交 | Capture panel | `POST /api/v1/captures` | implemented | 是 | 真触发 extraction + AI + consolidate |
| Capture URL 提交 | Capture panel | `POST /api/v1/captures` | implemented | 是 | 真做 HTTP snapshot + AI 分析 |
| Capture 结果卡片 | Capture panel | capture response | implemented | 是 | 显示状态、失败原因、analysis preview |
| Note List | Note panel | `GET /api/v1/notes` | implemented | 否 | 真实列表 |
| Note Detail | 右侧详情 | `GET /api/v1/notes/{id}` | implemented | 否 | 真实详情 |
| Evidence Blocks 展示 | Note Detail | `GET /api/v1/notes/{id}` | implemented | 否 | 真显示 `EVIDENCE` |
| Proposal 列表 | Note Detail | `GET /api/v1/notes/{id}/change-proposals` | implemented | 否 | 真实 proposal 数据 |
| Proposal 生成 | Note Detail | `POST /api/v1/notes/{id}/change-proposals` | implemented | 否 | 规则生成，不走 AI |
| Proposal apply | Note Detail | `POST /api/v1/notes/{id}/change-proposals/{proposalId}/apply` | implemented | 否 | 真更新 Note 解释层 |
| Proposal rollback | Note Detail | `POST /api/v1/change-proposals/{proposalId}/rollback` | implemented | 否 | 真回滚 Note 解释层 |
| Search exact matches | Search panel | `GET /api/v1/search` | implemented | 否 | 真基于本地 Note 数据 |
| Search related matches | Search panel | `GET /api/v1/search` | implemented | 是 | relation_reason 可被 AI 增强 |
| Search external supplements | Search panel | `GET /api/v1/search` | stub/mock | 是 | 来源是 stub seed，不是真外部检索 |
| Save search evidence | Search panel | `POST /api/v1/search/notes/{noteId}/evidence` | implemented | 部分 | supplement 可是 AI 或 fallback 结果 |
| Generate search proposal | Search panel | `POST /api/v1/search/notes/{noteId}/change-proposals` | partially implemented | 部分 | 仅 AI enhanced supplement 允许 |
| Workspace Today | Workspace panel | `GET /api/v1/workspace/today` | implemented | 部分 | 首屏直接返回基础 review/task；Review AI 已移出同步关键路径 |
| Workspace Upcoming | Workspace panel | `GET /api/v1/workspace/upcoming` | implemented | 否 | 真聚合 upcoming 数据 |
| Review Today 列表 | Workspace Today | workspace today response + `GET /api/v1/reviews/{id}/prep` | implemented | 是 | 首屏先显示基础摘要/关键点；展开某条 review 时懒加载 prep 并回填三段式卡片 |
| Review 完成表单 | Workspace Today | `POST /api/v1/reviews/{id}/complete` | implemented | 部分 | 真提交完成语义；主响应不再同步等待 AI feedback |
| Review AI 反馈展示 | Workspace Today | `GET /api/v1/reviews/{id}/feedback` | implemented | 是 | complete 成功后自动独立拉取 summary / hint / suggestion |
| Task Today 列表展示 | Workspace Today | workspace today response | implemented | 否 | 仅展示 |
| Task Upcoming 列表展示 | Workspace Upcoming | workspace upcoming response | implemented | 否 | 仅展示 |
| Task 创建 UI | 无 | `POST /api/v1/tasks` | not implemented | 否 | 后端有接口，前端无入口 |
| Task complete/skip UI | 无 | `POST /api/v1/tasks/{id}/complete|skip` | not implemented | 否 | 后端有接口，前端无入口 |
| Idea List | 无 | 无 | not implemented | 否 | 完全缺失 |
| Idea Detail | 无 | 无 | not implemented | 否 | 完全缺失 |
| Idea Assess | 无 | 无 | not implemented | 否 | 完全缺失 |
| Idea -> Task UI | 无 | 无 | not implemented | 否 | 完全缺失 |

## 2. 后端能力状态矩阵

| 模块 | 核心能力 | 状态 | AI | 说明 |
| --- | --- | --- | --- | --- |
| Capture | TEXT capture | implemented | 是 | 真链路 |
| Capture | URL capture | implemented | 是 | HTTP 文本快照 |
| Capture | 旧 Note 匹配 / merge | not implemented | 否 | 永远新建 Note |
| Capture | `UPDATE / APPEND / CONFLICT` 决策 | not implemented | 否 | 无此分支 |
| Note | list/detail query | implemented | 否 | 真实读库 |
| Note | 当前解释层更新 | implemented | 否 | 仅 proposal apply / rollback 使用 |
| Search | exact / related internal retrieval | implemented | 否 | 规则检索 |
| Search | external supplement seed generation | stub/mock | 否 | deterministic stub |
| Search | AI enhancement | implemented | 是 | route `search-enhancement` |
| Search Governance | save evidence | implemented | 否 | 落 `note_contents.EVIDENCE` |
| Search Governance | generate proposal | implemented | 否 | 基于 supplement + rule 生成 |
| Review | dual queue | implemented | 否 | `SCHEDULE / RECALL` |
| Review | completion semantics | implemented | 否 | 四种完成状态 |
| Review | AI render | implemented | 是 | route `review-render`，当前走独立 prep read |
| Review | AI feedback | implemented | 是 | route `review-feedback`，当前走独立 feedback read |
| Review | follow-up system task | implemented | 否 | 规则驱动 |
| Task | create user task | implemented | 否 | 去重校验已实现 |
| Task | today/upcoming query | implemented | 否 | 真实查询 |
| Task | complete / skip | implemented | 否 | 真实状态切换 |
| Task | start / cancel | not implemented | 否 | 无 API |
| Proposal | create | implemented | 否 | 规则生成 |
| Proposal | apply | implemented | 否 | 真更新 Note |
| Proposal | rollback | implemented | 否 | 真回滚 Note |
| Proposal | reject | not implemented | 否 | 无 API |
| Workspace | today aggregation | implemented | 否 | 真实聚合 today review + task，已不再同步等待 Review AI |
| Workspace | upcoming aggregation | implemented | 否 | Review + Task 聚合 |
| Idea | schema / state machine / API / UI | not implemented | 否 | 全部缺失 |

## 3. AI 接入点矩阵

| 接入点 | Route Key | 接口/实现 | 状态 | Trace / Log / Event |
| --- | --- | --- | --- | --- |
| Capture analysis | `capture-analysis` | `DefaultCaptureAnalysisClient` + `CaptureAnalysisWorker` | implemented | 完整 |
| Search enhancement | `search-enhancement` | `DefaultSearchAiEnhancer` | implemented | 完整 |
| Review render | `review-render` | `DefaultReviewAiAssistant.renderTodayItems` | implemented | 完整，当前用于 `/api/v1/reviews/{id}/prep` |
| Review feedback | `review-feedback` | `DefaultReviewAiAssistant.buildCompletionFeedback` | implemented | 完整，当前用于 `/api/v1/reviews/{id}/feedback` |
| Proposal generation | 无 | 规则逻辑 | not implemented as AI | 仅 trace/event/log，不是 AI |
| Task generation/planning | 无 | 规则逻辑 | not implemented as AI | 无独立 AI 接入 |
| Idea assess | 无 | 无 | not implemented | 无 |
| Trend research provider | 无 | 无 | not implemented | 无 |

## 4. 当前最值得警惕的“看起来有，实际没有”

| 表象 | 现实 |
| --- | --- |
| `TaskRelatedEntityType` 里有 `IDEA` | 只是预留，没有任何真实 Idea 流程使用 |
| Search 有 external supplements | 实际来源是 stub，不是真外部 provider |
| 前端有 `listReviewsToday` / `listTasksToday` client | 页面主路径并未使用 |
| 文档写 Phase 3 Idea 基线 | 代码里不存在 Idea 主线 |
