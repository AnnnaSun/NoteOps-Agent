# Review Preparation Card Design

日期：2026-04-03

## 1. 目标

在现有 Review AI 最小增强补丁基础上，继续实现一个更小的前端展示切片：优化 Today Workspace 中 Review 卡片的“复习前准备区”，让用户在开始复习前能更清楚地区分：

1. 主回忆摘要
2. 回忆支点
3. 必要延伸

本切片只调整前端展示层次，不新增后端业务能力，不改 Review 状态机，不改 AI 接口，不扩新字段，不调整 Review complete 的反馈链路。

## 2. 当前现实

根据当前代码和 `docs/reality`：

- Review Today 列表已经真实接入 `GET /api/v1/workspace/today`
- Review 卡片已经能显示：
  - `ai_recall_summary`
  - `ai_review_key_points`
  - `ai_extension_preview`
- 当前展示是“标题 + 一段摘要 + points + extension”的线性堆叠
- `review complete` 后的 AI feedback banner 已实现，不属于本切片主目标

现实问题不是字段缺失，而是信息层次不够清楚：

- 用户能看到 AI 内容，但不容易一眼判断“先看什么”
- `ai_recall_summary` 和 points 在视觉上区分度不足
- `ai_extension_preview` 目前只是挂在正文尾部，语义不够独立

## 3. 范围

### 3.1 本次要做

- 调整 Today Review 卡片的前端结构为三段式纵向布局
- 强化主区 `AI 回忆摘要`
- 弱化但保留 `回忆支点`
- 在有值时展示 `必要延伸`
- 保持现有 fallback：
  - `ai_recall_summary ?? current_summary`
  - `ai_review_key_points.length > 0 ? ai_review_key_points : current_key_points`
  - `ai_extension_preview` 为空则不展示第三段
- 按实际结果同步更新 `docs/reality` 中前端能力和实现库存文档

### 3.2 本次不做

- 不新增 API / DTO 字段
- 不增加新的 Review 动作按钮
- 不把卡片改成折叠/展开交互
- 不改 `lastReviewFeedback` banner 的功能或数据流
- 不新增“AI 成功/失败”状态展示
- 不扩展 upcoming review 页面
- 不改后端 trace / tool log / event 行为

## 4. 方案比较

### 方案 A：三段式纵向卡片（采用）

结构：

- 主区：`AI 回忆摘要`
- 次区：`回忆支点`
- 弱提示区：`必要延伸`

优点：

- 不改数据流，只改展示结构
- 与现有字段完全匹配
- 用户一眼能看懂主次关系
- 是后续继续补“规则回退提示”或“开始回忆提示”的稳定底座

缺点：

- 卡片高度会略有增加
- 需要在样式上克制，避免显得像完整详情卡

### 方案 B：仅做排版微调

优点：

- 风险最低
- 改动最少

缺点：

- 结构提升有限
- 很容易退化成“只是换了点样式”

### 方案 C：主摘要 + 行动提示条

优点：

- 行动感更强

缺点：

- 已经偏向“开始复习引导”，超出本切片范围
- 会把本次目标从“信息层次”拉到“行为引导”

## 5. 设计

### 5.1 组件结构

不做大规模前端拆分，保持当前 `web/src/App.tsx` 主体结构，只在 Review 卡片渲染处引入一个很小的展示辅助层。

建议的内部展示分层：

- Review 卡片头部
  - Note title
  - queue / due / unfinished 等既有 meta
- Section 1：`AI 回忆摘要`
  - 视觉最显眼
  - 展示短摘要文本
- Section 2：`回忆支点`
  - 用较轻视觉层级展示 2-4 个点
- Section 3：`必要延伸`
  - 仅在有值时显示
  - 采用提示性文案样式

这次不把它提炼成新的独立文件组件，避免范围被动扩大；只做局部结构重排和必要的样式补充。

### 5.2 数据与 fallback 规则

主区摘要：

- 优先：`review.ai_recall_summary`
- 回退：`review.current_summary`

支点列表：

- 优先：`review.ai_review_key_points`
- 回退：`review.current_key_points`
- 最多展示前 4 条，避免卡片过高

延伸提示：

- 使用：`review.ai_extension_preview`
- 若为空或空白：整个 section 不显示

说明：

- 不新增“AI / fallback”标签
- 不暴露 provider 或降级原因
- 只保证当 AI 字段不存在时，用户仍然能正常完成复习前阅读

### 5.3 视觉层次

视觉权重从高到低：

1. `AI 回忆摘要`
2. `回忆支点`
3. `必要延伸`

建议样式方向：

- `AI 回忆摘要`
  - 区块标题更明确
  - 正文样式接近卡片主文本
- `回忆支点`
  - 小标题 + 点状条目
  - 比摘要更轻
- `必要延伸`
  - 小标题 + 一条弱提示
  - 使用现有较轻文字样式

约束：

- 不做动画
- 不改变当前整体页面布局
- 不引入全局视觉主题变更

### 5.4 对现有反馈层的影响

`lastReviewFeedback` banner 保持原样。

原因：

- 它属于 Review 完成后反馈层，不是复习前准备区
- 若这次同时改动 banner，会把切片从“复习前层次优化”扩展成“Review UI 全面重排”

## 6. 代码修改边界

### 6.1 预期主要修改文件

- `web/src/App.tsx`
  - 重排 Today Review 卡片的结构
  - 增加少量 section 文案和样式 hook
- 如当前样式集中在同文件关联 CSS，则按现有项目方式补少量样式
- `docs/reality/Feature-Status-Matrix.md`
  - 更新 Review Today 列表的现实说明
- `docs/reality/Implementation-Inventory.md`
  - 更新前端 Review 卡片现实描述

### 6.2 明确不应修改的区域

- 后端 review service / controller / DTO
- AI route 配置
- Review complete 业务逻辑
- trace / tool log / user_action_events

## 7. 错误处理与降级

本切片不新增新的错误来源，主要依赖已有降级：

- `ai_recall_summary` 缺失时，继续显示 `current_summary`
- `ai_review_key_points` 缺失时，继续显示 `current_key_points`
- `ai_extension_preview` 缺失时，不显示第三段

如果某个 review item 既没有 AI 字段也没有足够的原始解释字段，仍沿用当前页面既有空态表现，不额外新增异常 UI。

## 8. 测试与验证

至少验证：

1. `npm run build` 通过
2. Today Review 卡片在有 AI 字段时按三段结构展示
3. Today Review 卡片在 AI 字段缺失时能回退显示原摘要和关键点
4. `lastReviewFeedback` banner 不被这次改动破坏
5. `docs/reality` 相关描述已同步为新的现实状态

当前 `web` 无现成前端测试脚本，本切片以 `npm run build` 和手工联调作为最小验证。

## 9. 文档同步要求

实现完成后，至少同步：

- `docs/reality/Feature-Status-Matrix.md`
- `docs/reality/Implementation-Inventory.md`

同步内容应只反映真实已实现结果，例如：

- Review Today 列表现在是“三段式准备区”展示
- `review complete` feedback banner 仍保留

不得把“未来可能做的规则回退标记”或“开始回忆提示条”写成已完成。

## 10. Deferred Notes

本切片完成后，仍未解决：

- Review AI 成功 / 降级可见性
- 更明确的“开始回忆”行动引导
- review feedback banner 的更稳定留存
- upcoming review 的同类展示优化
- 更细的 Review UI 组件拆分

## 11. 实现完成定义

只有满足以下条件，才算本切片完成：

1. Today Review 卡片形成明确的三段式纵向结构
2. 默认视觉焦点落在 `AI 回忆摘要`
3. `回忆支点` 与 `必要延伸` 层级低于主区
4. AI 字段缺失时，仍能正确 fallback
5. Review complete feedback banner 行为不变
6. `docs/reality` 已按真实结果同步
