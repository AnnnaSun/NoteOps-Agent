# Plan
# docs/codex/Plan.md

## 1. 当前阶段

当前开发阶段已从 Phase 3 切换到：

# Phase 4：Trend Source Registry / Trend Inbox

本阶段以 **Trend 正式最小闭环** 为唯一目标。
Preference Learning、PWA、移动端均不进入当前主线。

---

## 2. 阶段目标

Phase 4 需要完成的不是一个简单的“热点列表”，而是一个最小但真实可运行的 Trend 产品闭环：

1. 系统能从受控来源拉取趋势候选
2. 系统有默认 Trend Plan（HN + GitHub）
3. 候选能被结构化分析与评分
4. 候选进入 Trend Inbox
5. 用户能执行 Trend 决策动作
6. 趋势候选可转为 Note 或 Idea
7. 转 Idea 后复用已有 Idea 流程
8. 全链路保持 trace / event / structured logging / doc sync 一致

---

## 3. 当前冻结边界

### 3.1 Phase 4 必做

1. Trend 领域模型落地
2. Trend Source Registry 最小落地
3. Default Trend Plan 落地：
    - `HN`
    - `GITHUB`
4. Trend ingest 最小闭环
5. Trend AI 结构化分析最小切片
6. Trend Inbox 最小可用
7. Trend -> Note / Idea 转化
8. trace / log / event / docs 对齐

### 3.2 Phase 4 可预留但不做正式闭环

1. 用户自定义 Trend Plan UI
2. 更复杂的 source 权重与个性化规则
3. 正式 `user_preference_profiles` 对 Trend 排序生效
4. 多 provider 抓取平台化
5. 复杂去重、聚类与事件合并
6. 高保真 UI 重构

### 3.3 Phase 4 明确不做

1. 任意网站自由抓取
2. Preference 正式画像重算器
3. PWA 离线 Trend 抓取
4. 原生移动端
5. 导出中心
6. 多 provider 复杂 AI 平台化
7. 全自动静默生成大量 Note / Idea

---

## 4. Phase 4 切片计划

## Step 4.1：Trend schema / contract 基线

### 目标
先冻结 Phase 4 的后端合同和数据边界，防止后续 registry / ingest / inbox 漂移。

### 交付
1. `trend_items` 表 / migration（如仍需补齐）
2. enum / source type / action constants
3. entity / model / repository
4. request / response DTO
5. 基础 API contract
6. 文档同步到 Phase 4 语义

### 最小验收
- `trend_items` 可被持久化
- source type 与 action 常量可统一使用
- API contract 与 persistence 对齐
- 文档明确记录 Phase 4 已开始

### 风险
- 若字段设计过早泛化，会影响后续 default plan 与 inbox 合同
- 若 source/action 枚举没冻结，前后端会漂移

### 建议后续
进入 Step 4.2

---

## Step 4.2：Trend Source Registry + Default Trend Plan

### 目标
让 Trend 具备正式来源入口，而不是手工塞数据。

### 范围
最小支持：
1. `HN`
2. `GITHUB`

### 交付
1. `TrendSourceRegistry`
2. source connector interface
3. 默认 `HN` / `GITHUB` source registration
4. Default Trend Plan config
5. 最小调度或显式触发入口

### 最小验收
- registry 能注册并解析 HN/GitHub source
- 默认 Trend Plan 配置可读取
- 能触发一次默认 plan 的 ingest 流程
- 写入必要 trace/log

### deferred
- 用户自定义 source
- 多计划并行
- 复杂定时任务治理

### 建议后续
进入 Step 4.3

---

## Step 4.3：Trend ingest 最小闭环

### 目标
让外部候选真正进入系统，而不是只有 registry。

### 交付
1. 拉取趋势候选
2. 基础归一化
3. 去重 / 幂等（最小）
4. 写入 `trend_items`
5. 基础分数或 rank 字段写入
6. 必要的 trace/log

### 最小验收
- 至少能从一个 source 拉取候选并落库
- `trend_items` 包含 title/url/source_type
- 重复拉取不会无限重复写入
- trace/log 可查

### deferred
- 高级聚类
- 高级摘要抓取
- 更复杂评分

### 建议后续
进入 Step 4.4

---

## Step 4.4：Trend AI 分析最小切片（Phase 4 关键步骤）

### 目标
让 Trend 从“抓到一条热点”升级为“可决策的候选项”。

### 这是 Phase 4 的关键要求
若缺失本步骤，Phase 4 不能视为真正完成最小闭环。

### 交付
1. `TrendAnalysisService`
2. `TrendAgent` interface（可先最小实现）
3. analysis request / result contract
4. analysis payload 落库
5. 输出：
    - `summary`
    - `why_it_matters`
    - `topic_tags`
    - `note_worthy`
    - `idea_worthy`
    - `suggested_action`
6. `agent_traces` 记录
7. 结构化日志

### 可以接受的实现方式
- 单 provider
- 单 prompt
- stub / mock adapter
- 同步调用

### 不可接受的实现方式
- controller 中直接调用模型
- analysis 只返回自然语言长文本
- 没有落库
- 没有 trace/log
- analysis 结果直接静默创建 Note / Idea

### 最小验收
- analysis 真实可调用
- 能返回结构化分析结果
- `trend_items.extra_attributes` 或等价字段被写入
- trace/log 可查

### deferred
- 多模型路由
- 个性化排序
- prompt registry 平台化
- 复杂评分模板

### 建议后续
进入 Step 4.5

---

## Step 4.5：Trend Inbox

### 目标
让 Phase 4 在前端和 API 层可见、可决策、可演示。

### 交付
1. `GET /api/v1/trends/inbox`
2. 最小列表排序 / 过滤
3. 展示字段：
    - title
    - source_type
    - summary
    - score
    - suggested_action
4. 用户动作：
    - `IGNORE`
    - `SAVE_AS_NOTE`
    - `PROMOTE_TO_IDEA`

### 最小验收
- 可以浏览 Trend 候选列表
- 可以看到 AI 分析结果
- 可以执行至少一种用户动作
- 候选状态变化可追溯

### deferred
- 高级筛选
- 批量操作
- 复杂可视化排序

### 建议后续
进入 Step 4.6

---

## Step 4.6A：Trend -> Note 转化

### 目标
把 Trend 的价值先推进到 Note 主知识链路，优先保证最小闭环可验证。

### 交付
1. `SAVE_AS_NOTE`：生成 Note 并保留来源链
2. 必要时记录 Trend 与目标 Note 的关联 id
3. 相关 `user_action_events`
4. 相关 trace/log
5. 转化成功后前端跳转到 Note 详情

### 最小验收
- 至少能从一个 trend item 成功生成 Note
- 转化后来源链可追溯
- 相关事件与日志可查
- Note 详情页可直接读取新建结果

### deferred
- 自动批量转化
- 智能二次整理
- 转化后的复杂 proposal 治理

### 建议后续
Step 4.6B 已完成，见 `docs/codex/Documentation.md` 中的当前落地状态。

---

## Step 4.6B：Trend -> Idea 转化（已完成）

### 目标
把 Trend 的价值真正推进到 Idea 主知识链路。

### 交付
1. `PROMOTE_TO_IDEA`：生成 Idea 并保留来源链
2. 必要时记录 Trend 与目标 Idea 的关联 id
3. 相关 `user_action_events`
4. 相关 trace/log
5. Trend -> Idea 后允许走既有 assess 流程

### 最小验收
- 至少能从一个 trend item 成功生成 Idea
- 转化后来源链可追溯
- 相关事件与日志可查

### deferred
- 自动批量转化
- 智能二次整理
- 转化后的复杂 proposal 治理

### 建议后续
进入 Step 4.7

---

## Step 4.7：Phase 4 文档与治理收口

### 状态
已完成。

### 目标
确保实现、文档、仓库规范一致，不留明显漂移。

### 交付
1. 更新 `docs/codex/Documentation.md`
2. 更新 milestone 状态
3. 记录 deferred backlog
4. 标记已完成与未完成边界
5. 校验 `AGENTS.md` / `Implement.md` / skill 是否仍与当前阶段一致

### 最小验收
- 文档能准确描述当前 Phase 4 实现范围
- 未完成内容被显式记录
- 没有把 Phase 5 能力误写成已实现

---

## 5. 阶段完成定义

仅当以下条件同时满足，才可以说 Phase 4 达到最小闭环：

1. 有默认 Trend Plan
2. 能从 HN / GitHub 拉取候选
3. 候选能做结构化 Trend 分析
4. 有 Trend Inbox
5. 用户能执行 ignore / save as note / promote to idea
6. 趋势候选可真实转为 Note / Idea
7. trace / log / event / docs 已同步

只完成表结构、静态列表页或手工写库，不算 Phase 4 闭环。

---

## 6. Deferred Backlog 记录要求

凡是为了最小闭环暂时跳过的能力，必须在 `Documentation.md` 中记录：

1. 跳过了什么
2. 为什么现在不做
3. 预期在哪个 Phase 补回
4. 当前造成什么限制

阶段性跳过，不代表永久删除。
