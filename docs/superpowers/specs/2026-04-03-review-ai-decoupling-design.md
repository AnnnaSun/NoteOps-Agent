# Review AI Decoupling Design

日期：2026-04-03

## 1. 目标

把 Review AI 从首页和 review complete 的同步关键路径中移出，解决本地 Ollama 慢导致的首屏阻塞与提交阻塞问题。

本切片的目标是：

1. `GET /api/v1/workspace/today` 与 `GET /api/v1/reviews/today` 不再同步等待 Review AI
2. Review prep AI 改成按条目懒加载
3. `POST /api/v1/reviews/{reviewItemId}/complete` 不再同步等待 AI feedback
4. Review feedback 改成 complete 成功后的独立读取接口

## 2. 当前现实

当前代码现实：

- 首页会调用 `GET /api/v1/workspace/today`
- 后端 `WorkspaceApplicationService.today(...)` 先查 review，再查 task
- `ReviewApplicationService.listToday(...)` 当前会同步调用 `enrichTodayItemsWithAi(...)`
- `ReviewApplicationService.complete(...)` 当前会在主业务成功后同步调用 AI feedback 构建
- 本地 provider 如果是 Ollama，`review-render` / `review-feedback` 延迟明显，就会直接拖慢首页和提交结果返回

这会带来两个问题：

1. 首页首屏长期空白或只显示 0 项，不符合“先显示已有稳定信息”的原则
2. Review list 为了拿到 AI recall 视图而依赖 AI，同步耦合过重

## 3. 领域边界澄清

### 3.1 哪些内容应该直接来自持久层

这些是 Note 当前解释层，应该直接查库返回：

- `title`
- `current_summary`
- `current_key_points`
- `current_tags`

这些字段已经是稳定持久字段，不需要为了列表首屏再走一次 AI。

### 3.2 哪些内容属于场景化 AI 视图

这些不是基础事实层，而是 Review 场景的再表达：

- `ai_recall_summary`
- `ai_review_key_points`
- `ai_extension_preview`
- `recall_feedback_summary`
- `next_review_hint`
- `extension_suggestions`
- `follow_up_task_suggestion`

它们适合懒加载或独立缓存，但不应该阻塞列表主查询，也不应该覆盖 `notes.current_*`。

## 4. 范围

### 4.1 本次要做

- 从 `listToday` 中移除同步 `enrichTodayItemsWithAi(...)`
- 从 `complete` 中移除同步 AI feedback 调用
- 新增 Review prep read 接口
- 新增 Review feedback read 接口
- 前端改为：
  - 首页只显示基础数据
  - 用户展开某条 review 时再请求 prep
  - complete 成功后自动请求 feedback
- 更新相关文档，明确 Review AI 已不在首页/complete 的同步关键路径

### 4.2 本次不做

- 不引入后台 job / 队列 / 轮询任务系统
- 不做 AI 结果持久化缓存
- 不改 Review 状态机
- 不改 ReviewScheduler 主算法
- 不改 follow-up task 规则层
- 不改 Idea / Task / Search 主链

## 5. 方案比较

### 方案 A：新增两个独立 read 接口（采用）

新增：

- `GET /api/v1/reviews/{reviewItemId}/prep`
- `GET /api/v1/reviews/{reviewItemId}/feedback`

优点：

- 主业务和 AI 视图完全解耦
- 接口语义清楚
- 最符合“首屏先显示稳定数据”的目标
- 不需要复杂调度

缺点：

- 多两个接口
- 前端需要管理局部 loading 状态

### 方案 B：现有接口加 `include_ai` query 开关

优点：

- 改动表面更小

缺点：

- controller / service 语义会变混
- 容易重新把 AI 耦回同步主链
- 长期维护性差

### 方案 C：引入 AI job + 轮询

优点：

- 最适合未来慢 provider

缺点：

- 对当前最小切片过重
- 会引入新的调度复杂度

## 6. 设计

### 6.1 后端接口设计

保留现有：

- `GET /api/v1/reviews/today`
- `GET /api/v1/workspace/today`
- `POST /api/v1/reviews/{reviewItemId}/complete`

但语义调整为：

#### `GET /api/v1/reviews/today`

- 只返回基础 ReviewTodayItem 数据
- 不再同步填充：
  - `ai_recall_summary`
  - `ai_review_key_points`
  - `ai_extension_preview`
- 如 DTO 仍保留这些字段，则统一返回 `null` / 空数组

#### `GET /api/v1/workspace/today`

- 同样只返回基础 today reviews + tasks
- 不因 Review AI render 阻塞 tasks 返回

#### `POST /api/v1/reviews/{reviewItemId}/complete`

- 只负责主业务：
  - completion status 处理
  - recall queue / schedule queue 规则
  - task 同步
  - trace / event / log
- 不再同步返回 AI feedback 字段
- 如当前 DTO 仍保留这些字段，则统一返回 `null` / 空数组

新增：

#### `GET /api/v1/reviews/{reviewItemId}/prep`

请求参数：

- `user_id`

响应字段：

- `review_item_id`
- `ai_recall_summary`
- `ai_review_key_points`
- `ai_extension_preview`

语义：

- 只读
- best-effort
- 失败不影响 Review 主状态

#### `GET /api/v1/reviews/{reviewItemId}/feedback`

请求参数：

- `user_id`

响应字段：

- `review_item_id`
- `recall_feedback_summary`
- `next_review_hint`
- `extension_suggestions`
- `follow_up_task_suggestion`

语义：

- 只读
- 基于当前 review state 和 note 生成 feedback
- 失败不影响 complete 主结果

### 6.2 服务层边界

`ReviewAiAssistant` 保留，不需要推翻。

业务编排调整为：

- `ReviewApplicationService.listToday(...)`
  - 只负责 today 基础数据
- 新增类似：
  - `ReviewApplicationService.getPrep(...)`
  - `ReviewApplicationService.getFeedback(...)`
- `ReviewApplicationService.complete(...)`
  - 不再内部同步调用 AI feedback

这意味着：

- `enrichTodayItemsWithAi(...)` 不再被首页路径调用
- 原 AI render / feedback 逻辑可以迁移为独立 read 能力

### 6.3 前端状态流

#### 首页加载

- 调 `getWorkspaceToday(...)`
- 立即显示基础 review/task
- Review 卡片默认只用：
  - `current_summary`
  - `current_key_points`
- 不自动请求 prep AI

#### 用户展开某条 review

- 若该条 prep 尚未加载，则请求 `GET /api/v1/reviews/{id}/prep`
- 加载中只影响该条卡片
- 成功后把卡片切换为 recall-friendly prep 视图
- 失败则继续显示基础字段，不抛全局错误

#### 用户提交 complete

- 先调 `POST /api/v1/reviews/{id}/complete`
- 主业务成功后刷新 workspace 或局部状态
- 然后自动请求 `GET /api/v1/reviews/{id}/feedback`
- feedback 返回前显示轻量 loading 占位
- feedback 失败时不影响 complete 成功状态

### 6.4 DTO 策略

本次建议最小兼容：

- 保留现有 `ReviewTodayItemResponse` 与 `ReviewCompletionResponse` 的 AI 字段
- 但在同步主接口里不再填充它们

原因：

- 避免一次切片同时触发大面积 DTO 破坏性收缩
- 让前端可以逐步迁移到独立 prep / feedback 响应

新增两个最小 response DTO 即可。

## 7. 可观测性

### 7.1 Today list

- 首页路径不再写 `REVIEW_AI_RENDER` trace
- `REVIEW_AI_RENDER` trace 转移到 `GET /api/v1/reviews/{reviewItemId}/prep`

### 7.2 Review complete

- `POST /complete` 继续写主业务 trace / event / logs
- AI feedback trace/log/event 转移到 `GET /feedback`

这样 trace 语义更清楚：

- 主业务成功与否
- AI 视图是否成功

不再混在同一个同步返回里。

## 8. 错误处理与降级

Prep 失败：

- 前端继续显示基础 summary / key points
- 后端记录 AI render failure trace / log / event

Feedback 失败：

- 前端不阻断 complete 成功提示
- feedback 区可不显示或显示轻量失败态
- 后端记录 AI feedback failure trace / log / event

## 9. 代码修改边界

### 9.1 预期主要修改文件

- `server/src/main/java/com/noteops/agent/controller/review/ReviewController.java`
- `server/src/main/java/com/noteops/agent/service/review/ReviewApplicationService.java`
- `server/src/main/java/com/noteops/agent/dto/review/*`
- `server/src/test/java/com/noteops/agent/controller/review/ReviewControllerTest.java`
- `server/src/test/java/com/noteops/agent/service/review/ReviewApplicationServiceTest.java`
- `web/src/api.ts`
- `web/src/types.ts`
- `web/src/App.tsx`
- `docs/reality/Feature-Status-Matrix.md`
- `docs/reality/Implementation-Inventory.md`
- `docs/codex/Documentation.md`

### 9.2 明确不应修改的区域

- review state schema
- ReviewScheduler 主算法
- task 持久化规则
- note 原始内容

## 10. 测试与验证

至少覆盖：

1. `workspace/today` 不再同步触发 AI render
2. `reviews/today` 仍可正常返回基础数据
3. `GET /reviews/{id}/prep` 可独立返回 AI prep
4. `POST /reviews/{id}/complete` 主业务成功时不再等待 AI feedback
5. `GET /reviews/{id}/feedback` 可独立返回 AI feedback
6. prep / feedback AI 失败不影响主流程
7. 前端首屏可立即显示 review/task，不再因 Ollama 阻塞长期空白

## 11. 文档同步要求

实现后至少同步：

- `docs/reality/Feature-Status-Matrix.md`
- `docs/reality/Implementation-Inventory.md`
- `docs/codex/Documentation.md`

需要明确写清：

- Review AI 已从 today / complete 的同步关键路径移出
- prep 改为展开时懒加载
- feedback 改为 complete 后独立加载

## 12. Deferred Notes

本切片完成后，仍未解决：

- AI prep / feedback 的结果缓存
- 批量 prep hydration
- feedback 的后台重试
- review 详情页专门的 prep 面板
- 更成熟的慢 provider 降级策略

## 13. 实现完成定义

只有满足以下条件，才算本切片完成：

1. 首页 today 首屏不再被 Review AI 阻塞
2. 展开 review 时才触发 prep AI
3. complete 主业务返回不再等待 feedback AI
4. complete 后会自动独立拉取 feedback
5. prep / feedback 失败不影响主链
6. 文档同步到真实实现
