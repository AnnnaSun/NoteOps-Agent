# Documentation
# docs/codex/Documentation.md

## 1. 当前阶段状态

当前仓库开发基线已进入：

# Phase 5：Preference + PWA

本阶段从 Phase 4 的 Trend 最小闭环继续向前推进，当前新增主线为：
- `user_action_events` 行为信号层
- `user_preference_profiles` 最小画像层
- preference recompute / refresh
- context injection 到建议层
- PWA 基础壳
- 有限离线 review 与 sync 回传

移动端、复杂推荐平台、全站离线仍未进入当前主线。

### 1.1 Step 5.1 当前落地状态

当前仓库已完成 Step 5.1 的最小 `user_action_events` 覆盖面补齐：
- Trend 关键动作已写事件：`TREND_ITEM_IGNORED`、`TREND_SAVED_AS_NOTE`、`TREND_PROMOTED_TO_IDEA`
- Review 关键动作已写事件：`REVIEW_COMPLETED`、`REVIEW_PARTIAL`、`REVIEW_NOT_STARTED`、`REVIEW_ABANDONED`
- Task 关键动作已写事件：`TASK_CREATED`、`TASK_COMPLETED`、`TASK_SKIPPED`
- Proposal 关键动作已写事件：`CHANGE_PROPOSAL_CREATED`、`CHANGE_PROPOSAL_APPLIED`、`CHANGE_PROPOSAL_REJECTED`、`CHANGE_PROPOSAL_ROLLED_BACK`

当前实现说明：
- Proposal reject 已提供真实接口 `POST /api/v1/change-proposals/{proposalId}/reject`
- reject 只允许 `PENDING_REVIEW -> REJECTED`，不修改 Note 解释层
- reject 链路已补齐 `agent_traces`、`tool_invocation_logs`、`user_action_events`

当前 Step 5.1 仍明确不做：
- 更细粒度的 UI 交互事件
- session 分析与漏斗分析

### 1.2 Step 5.2 当前落地状态

当前仓库已完成 Step 5.2 的最小 `user_preference_profiles` 基线：
- 已新增 `user_preference_profiles` 表，按 `user_id` 一行持久化当前 profile
- 当前只支持 `interest_profile`
- 已提供 repository / application service / controller / DTO 读写基线
- 已提供最小查询与保存接口：
  - `GET /api/v1/preferences/profile?user_id=...`
  - `PUT /api/v1/preferences/profile`

当前实现说明：
- `interest_profile` 结构已稳定为 `preferred_topics`、`ignored_topics`、`source_weights`、`action_bias`、`task_bias`
- 保存链路会写结构化日志
- 保存链路会写 `agent_traces`，entry type 为 `USER_PREFERENCE_PROFILE_UPSERT`
- 当前 profile 仍是静态持久化结果，不会自动从 `user_action_events` 重算

当前 Step 5.2 仍明确不做：
- `output_style_profile`
- `agent_policy_profile`
- 多版本 profile 回退
- 事件聚合生成 profile 的 recompute / refresh

---

## 2. Phase 5 目标说明

Phase 5 的目标不是做一个复杂推荐系统，也不是做“任何时候都能离线的全功能客户端”。

当前阶段要完成两个最小但真实的闭环：

1. **行为 -> 偏好 -> 建议**
2. **缓存 -> 离线动作 -> 回传同步**

Preference 主线解决的问题：
- 系统能否逐步学习用户真正关心的主题、来源与行为偏好
- 学到的结果能否以可控方式影响现有 Agent / 排序 / suggested action

PWA 主线解决的问题：
- 在弱网或离线情况下，用户能否继续完成最关键的 review 行为
- 这些行为能否在联网后回传并合并

---

## 3. Phase 5 领域语义

### 3.1 UserActionEvent 的定位

`user_action_events` 不是普通审计日志，而是：
- 偏好学习输入
- Agent 评估输入
- 排序 / suggested action 调整输入
- 后续长期记忆注入输入

当前阶段优先覆盖以下行为：
- trend ignored / saved / promoted
- review completed / partial / not_started
- task completed / skipped
- proposal applied / rejected / rolled_back

### 3.2 UserPreferenceProfile 的定位

`user_preference_profiles` 是长期偏好画像。
当前阶段优先实现：
- `interest_profile`

后置：
- `output_style_profile`
- 更复杂的 `agent_policy_profile`

当前阶段遵循冻结语义：
- 系统先学习“用户关心什么”
- 再学习“用户怎么表达”
- 学习结果默认进入建议层，而不是静默覆盖产品输出

### 3.3 Preference 注入边界

Preference 在当前阶段只能影响：
- Trend 候选排序
- Trend suggested action
- Search 的最小相关性排序或补充建议排序
- Task / Idea 的建议优先级（若当前步骤已实现）

Preference 在当前阶段不得直接影响：
- 原始正文
- 已确认的业务最终状态
- 未经确认的关键内容改写

### 3.4 PWA / Offline 的定位

当前阶段的离线能力是有限离线：
- 允许查看已缓存 Note 摘要
- 允许查看 Today Review
- 允许完成基础 review
- 允许写简短备注
- 允许本地保存 pending actions 并联网回传

明确禁止：
- 离线外部检索
- 离线 URL 抽取
- 离线 Trend 抓取
- 离线深度 LLM 分析
- 离线 proposal apply / rollback

---

## 4. 最小 Preference 闭环

### 4.1 为什么这是 Phase 5 必须项

如果没有行为信号与 profile 产物，Preference 只会停留在文档概念层。

因此，Phase 5 最小闭环明确要求提供：
- `user_action_events`
- `user_preference_profiles`
- recompute / refresh 机制
- 至少一个读取 profile 的真实使用点
- trace / log / event / docs 对齐

### 4.2 Preference 的管理原则

Preference 必须按“行为驱动 + 建议层注入”管理，而不是自由自改写系统。

系统负责：
- 记录用户行为
- 聚合生成 profile
- 在建议层注入 profile
- 记录注入链路与结果

系统不负责：
- 静默改写最终文本
- 跳过规则系统直接强制排序或覆盖
- 把 preference 当成万能配置中心

### 4.3 `interest_profile` 建议合同

当前阶段建议最小结构如下：

```json
{
  "preferred_topics": ["string"],
  "ignored_topics": ["string"],
  "source_weights": {
    "HN": 0.8,
    "GITHUB": 1.0,
    "SEARCH_EXTERNAL": 0.4
  },
  "action_bias": {
    "save_as_note": 0.7,
    "promote_to_idea": 0.9,
    "ignore_trend": 0.2
  },
  "task_bias": {
    "review": 0.8,
    "idea_followup": 1.0
  }
}
```

说明：
- 当前阶段不要求复杂画像维度
- 当前阶段不要求多版本实验平台
- 当前阶段优先保证结构稳定、可生成、可读取、可解释

---

## 5. API / Contract 基线（Phase 5）

### 5.1 UserActionEvent

当前阶段不一定需要直接暴露完整外部 CRUD API，但至少需要：
- 服务端有统一写入入口或内部 service
- 文档明确 action_type 语义
- 关键动作在 trace / log 中可关联

当前已落地的关键 action_type 至少包括：
- Trend：`TREND_ITEM_IGNORED`、`TREND_SAVED_AS_NOTE`、`TREND_PROMOTED_TO_IDEA`
- Review：`REVIEW_COMPLETED`、`REVIEW_PARTIAL`、`REVIEW_NOT_STARTED`、`REVIEW_ABANDONED`
- Task：`TASK_CREATED`、`TASK_COMPLETED`、`TASK_SKIPPED`
- Proposal：`CHANGE_PROPOSAL_CREATED`、`CHANGE_PROPOSAL_APPLIED`、`CHANGE_PROPOSAL_REJECTED`、`CHANGE_PROPOSAL_ROLLED_BACK`

### 5.2 Preference Profile

当前已落地：
- `GET /api/v1/preferences/profile?user_id=...`
- `PUT /api/v1/preferences/profile`

当前最小响应语义：
- `id`
- `user_id`
- `interest_profile`
- `created_at`
- `updated_at`

当前最小写入语义：
- `user_id`
- `interest_profile`

说明：
- Step 5.2 只提供 profile 查询与保存基线
- recompute / refresh 仍属于 Step 5.3

### 5.3 Sync Actions

`POST /api/v1/sync/actions`

用途：
- 客户端回传离线动作
- 服务端执行幂等校验与最小合并

最小输入语义：
- `client_id`
- `actions[]`
    - `offline_action_id`
    - `action_type`
    - `entity_type`
    - `entity_id`
    - `payload`
    - `occurred_at`

最小输出语义：
- `accepted[]`
- `rejected[]`
- `server_sync_cursor`

---

## 6. 最小 PWA / Offline 闭环

### 6.1 为什么这是 Phase 5 必须项

如果只有 manifest 和 service worker，没有真实离线行为链路，那么 PWA 只是包装，不是产品能力。

因此，Phase 5 的最小闭环要求至少包含：
- PWA 基础壳
- review 主路径缓存
- 离线 review 动作记录
- `sync/actions` 回传
- 幂等与最小错误处理

### 6.2 推荐缓存对象

当前阶段优先缓存：
- Today Review 队列
- Note 摘要
- 基础 task 列表（如果当前 Web 已依赖）
- 核心静态资源

当前阶段不优先缓存：
- 大量历史数据
- 外部 evidence 搜索结果
- Trend 原始抓取结果
- 大体量媒体或附件

### 6.3 Pending Actions 语义

客户端离线动作建议按 action log 保存，而不是直接本地覆盖最终状态。

推荐最小字段：
- `offline_action_id`
- `action_type`
- `entity_type`
- `entity_id`
- `payload`
- `occurred_at`
- `sync_status`

这样可以与服务端真相源模式保持一致。

---

## 7. 可观测性与治理要求（Phase 5）

Phase 5 新增核心链路必须补齐：

1. 结构化日志
2. `agent_traces`
3. 必要时的 `tool_invocation_logs`
4. `user_action_events`
5. sync 合并结果记录

最小日志点应覆盖：
- 关键用户动作写事件
- profile recompute 开始 / 成功 / 失败
- preference 注入开始 / 成功 / 失败
- 离线动作保存本地
- sync 请求入口
- action 接受 / 拒绝 / 幂等忽略

日志至少应包含：
- `trace_id`
- `user_id`
- `action`
- 关键业务 id
- `result`
- `duration_ms`（如适用）
- `error_code` / `error_message`（失败时）

---

## 8. Web / PWA 交付基线（Phase 5）

当前阶段前端最小目标：

1. PWA 基础安装能力
2. 离线时可打开已缓存核心页面
3. Today Review 主路径最小可用
4. 离线动作可记录并反馈状态
5. 联网后可触发同步或自动回传
6. 加载 / 空 / 错误 / 离线状态清晰

当前阶段前端不优先：
- 复杂视觉重构
- 全站离线
- 离线高级搜索
- 多端统一 UI 壳

---

## 9. 当前 deferred backlog

以下能力当前阶段可以明确后置，但不能丢失：

1. `output_style_profile` 深化学习
    - 原因：当前先优先学习用户关心什么
    - 预计补回：Phase 5 后段或后续优化阶段

2. 更复杂的 preference ranking / recommendation
    - 原因：当前先保证行为 -> 画像 -> 建议的最小闭环
    - 预计补回：Phase 5 后段或 Phase 6 以后

3. 全站离线与更复杂同步冲突解决
    - 原因：当前只做有限离线主路径
    - 预计补回：后续 PWA 强化阶段

4. 原生移动端
    - 原因：路线图中属于更后阶段
    - 预计补回：Phase 6

5. 多 provider / prompt platform 深化
    - 原因：当前不是模型平台化阶段
    - 预计补回：后续能力增强阶段

---

## 10. 当前完成定义

仅当以下条件同时满足，才可标记为“Phase 5 已完成最小闭环”：

1. 关键用户动作可写入 `user_action_events`
2. `user_preference_profiles` 已有最小产物
3. 至少存在一条真实的 recompute / refresh 链路
4. 至少一个现有能力读取 profile 并形成建议层注入
5. 前端具备 PWA 基础壳
6. 可离线完成至少一条 review 主路径
7. `sync/actions` 回传与幂等合并可用
8. trace / log / event / docs 已同步

如果只完成事件表、只完成 profile 表、或只做 PWA 外壳，都不可标记为已完成最小闭环。
