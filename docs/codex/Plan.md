# Plan
# docs/codex/Plan.md

## 1. 当前阶段

当前开发阶段已进入：

# Phase 5：Preference + PWA

本阶段不是简单“补功能”，而是把系统从“能吸收并转化知识”推进到“会逐步理解用户，并且在有限离线场景下继续运作”。

---

## 2. 阶段目标

Phase 5 需要完成两个最小但真实可运行的闭环：

1. **Preference Signal Layer**
    - 记录用户行为
    - 生成偏好画像
    - 将画像以建议层 context injection 注入现有能力

2. **PWA Limited Offline Review + Sync**
    - 提供 PWA 基础壳
    - 支持离线 review 最小闭环
    - 支持 pending actions 回传与服务端合并

---

## 3. 当前冻结边界

### 3.1 Phase 5 必做

1. `user_action_events` 关键事件链路补齐
2. `user_preference_profiles` 最小结构落地
3. `interest_profile` recompute / refresh 最小闭环
4. 至少一个现有能力读取 preference 并形成建议层注入
5. PWA 基础壳
6. 离线 review 最小闭环
7. `POST /api/v1/sync/actions` 回传闭环
8. trace / log / event / docs 对齐

### 3.2 Phase 5 可预留但不做正式闭环

1. `output_style_profile` 深化学习
2. 更复杂的推荐系统
3. 更多离线对象与更强同步能力
4. 多端统一离线 SDK
5. 更复杂的 prompt / model routing
6. 移动端正式实现

### 3.3 Phase 5 明确不做

1. 原生 Android / iOS
2. 任意离线外部检索
3. 离线 URL 抽取
4. 离线 Trend 抓取
5. 离线深度 LLM 分析
6. 全站离线编辑
7. 复杂商业推荐平台

---

## 4. Phase 5 切片计划

## Step 5.1：UserActionEvent 覆盖面补齐

### 目标
把当前真实用户动作沉淀成长期偏好学习与评估输入，而不是零散埋点。

### 交付
1. `user_action_events` 领域模型 / repository / DTO / service 对齐
2. 覆盖 Trend、Review、Task、Proposal 关键动作
3. 关键动作写入 trace/log/event
4. 文档明确记录 action_type 范围

### 最小验收
- 至少一条 Trend 动作被写入事件表
- 至少一条 Review 动作被写入事件表
- 至少一条 Task 或 Proposal 动作被写入事件表
- 事件带 `user_id`、`entity_type`、`entity_id`、`action_type`

### 当前状态
已完成最小闭环。当前仓库已覆盖 Trend、Review、Task、Proposal 的关键动作事件，并补齐 proposal reject 的真实治理链路。

### deferred
- 更细粒度的 UI 交互事件
- session 分析与漏斗分析

### 建议后续
进入 Step 5.2

---

## Step 5.2：Preference Profile 基线

### 目标
落地 `user_preference_profiles` 的最小结构，先支持 `interest_profile`。

### 交付
1. `user_preference_profiles` 表 / migration（若尚未落地）
2. entity / model / repository
3. request / response DTO（若当前阶段需要）
4. 最小 `interest_profile` 合同
5. 文档同步 Phase 5 语义

### 最小验收
- profile 可被持久化
- `interest_profile` 字段结构稳定
- 与事件模型能形成后续联动基础

### 当前状态
已完成最小闭环。当前仓库已落地 `user_preference_profiles` 表、`interest_profile` 最小合同、repository / service / controller / DTO 基线，并提供 profile 查询与保存接口。

### deferred
- `output_style_profile`
- `agent_policy_profile`
- 复杂多版本回退

### 建议后续
进入 Step 5.3

---

## Step 5.3：Preference Recompute / Refresh 最小闭环

### 目标
让系统不只是存 profile，而是真能从行为生成 profile。

### 交付
1. `PreferenceRecomputeService`
2. 从 `user_action_events` 聚合生成 `interest_profile`
3. 手动触发或最小 job 触发
4. profile 更新日志与 trace

### 最小验收
- 至少能对一个用户生成 profile
- profile 内容能反映真实 Trend / Review / Task 行为倾向
- 更新链路可追踪

### 当前状态
已完成最小闭环。当前仓库已新增 `PreferenceRecomputeService`（手动触发），可从 `user_action_events` 最近窗口聚合并重算 `interest_profile`，并通过 trace / structured logging 记录重算开始、成功、失败链路。

### deferred
- 复杂权重学习
- 批量离线重算平台
- 在线实时流式更新

### 建议后续
进入 Step 5.4

---

## Step 5.4：Preference Context Injection

### 目标
让 Preference 真正影响现有系统，但只进入建议层，不静默覆盖最终结果。

### 交付
1. Trend suggested action / 排序增强
2. Search 相关性或 external suggestion 的最小排序增强
3. 必要的上下文注入 service / mapper
4. 注入链路的日志与文档

### 最小验收
- 至少一个现有能力读取 profile
- 注入结果能影响排序或 suggested_action
- 不直接改写正文 / 最终状态

### 当前状态
已完成最小闭环。当前仓库已新增 `PreferenceContextInjectionService`，并在 Trend Inbox 与 Search 链路接入 `interest_profile` 的建议层注入：可在运行时影响 Trend 候选排序与 `suggested_action`、Search related 排序，同时保持“不改写持久化最终状态”边界。

### deferred
- 全局统一推荐层
- 更复杂个性化排序
- 多 Agent 全面共享 profile

### 建议后续
进入 Step 5.5

---

## Step 5.5：PWA 基础壳

### 目标
建立有限离线能力所需的最小前端基础设施。

### 交付
1. manifest
2. service worker
3. 核心静态资源缓存
4. 基础数据缓存策略说明
5. 前端基础安装/离线可访问能力

### 最小验收
- Web 可被安装为 PWA（若当前技术栈支持）
- 基础壳离线可打开
- 已缓存页面具备最小访问能力

### 当前状态
已完成最小闭环。当前仓库已补齐 `manifest.webmanifest`、`service worker` 注册与核心静态资源缓存策略，并对 Review / Note summary / Task 相关 GET 请求提供最小数据缓存降级能力，满足 Step 5.5 的 PWA 基础壳要求。

### deferred
- 深度缓存优化
- 更复杂更新策略
- 多页面精细缓存控制

### 建议后续
进入 Step 5.6

---

## Step 5.6：Offline Review + Sync 最小闭环

### 目标
让最重要的离线主路径真实可用。

### 交付
1. 缓存 Today Review / Note 摘要 / 基础 task 数据
2. 离线完成 review
3. 记录 pending actions
4. `POST /api/v1/sync/actions`
5. 服务端幂等与基本合并
6. 关键同步日志与错误处理

### 最小验收
- 可在离线状态完成至少一个 review
- 动作会保存在本地 pending actions
- 联网后能成功回传并被服务端接受
- 重复回传具备基本幂等性

### 当前状态
已完成最小闭环。当前仓库已在 Web 端支持离线 review 动作本地 pending 记录与联网自动回传，并新增 `POST /api/v1/sync/actions` 服务端合并入口；服务端已落地按 `(user_id, client_id, offline_action_id)` 的幂等 receipt，重复回传会命中既有结果而不重复执行 review 完成主链路。

### deferred
- 更多离线动作类型
- 更复杂冲突解决
- 后台同步优化

### 建议后续
进入 Step 5.7

---

## Step 5.7：Phase 5 文档与治理收口

### 状态
已完成。

### 目标
确保实现、文档、日志、边界一致，不留明显漂移。

### 交付
1. 更新 `docs/codex/Documentation.md`
2. 更新 milestone 状态
3. 记录 deferred backlog
4. 标记已完成与未完成边界
5. 校验 `AGENTS.md` / `Implement.md` / skill 是否仍与当前阶段一致

### 最小验收
- 文档准确描述当前 Phase 5 已实现能力
- 未完成内容显式记录
- 未把未来移动端/复杂推荐误写成已实现

### 当前状态
已完成最小闭环。当前仓库已完成 Step 5.1 ~ Step 5.6 的实现与文档对齐，并在 Step 5.7 完成 milestone 状态收口、deferred backlog 明确化、Phase 边界澄清与执行文档一致性校验。

### deferred
- Phase 6 目标与切片计划尚未在本文件展开
- AGENTS.md 仍保留 Phase 4 章节作为历史冻结边界说明（不影响当前按 Phase 5 执行）

### 建议后续
进入 Phase 6 规划（仅规划，不在当前步骤实现）

---

## 5. 阶段完成定义

仅当以下条件同时满足，才可以说 Phase 5 达到最小闭环：

1. 已有真实 `user_action_events` 链路
2. 已有最小 `user_preference_profiles`
3. 已有至少一种 recompute / refresh 机制
4. 已有至少一个现有能力读取 profile 形成建议层注入
5. Web 已具备 PWA 基础壳
6. 已有离线 review 主路径
7. 已有 `sync/actions` 回传与幂等合并
8. trace / log / event / docs 已同步

如果只做事件表或只做 PWA 壳子，不算 Phase 5 最小闭环。

当前状态：以上 8 条条件均已满足，Phase 5 已达到最小闭环。

---

## 6. Deferred Backlog 记录要求

凡是为了最小闭环暂时跳过的能力，必须在 `Documentation.md` 中记录：

1. 跳过了什么
2. 为什么现在不做
3. 预期在哪个 Phase 或后续子步骤补回
4. 当前造成什么限制

阶段性跳过，不代表永久删除。
