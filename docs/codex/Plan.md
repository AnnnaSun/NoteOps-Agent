# Plan
# docs/codex/Plan.md

## 1. 当前阶段

当前开发阶段已从 Phase 2 切换到：

# Phase 3：Idea Lifecycle / Idea Workspace

本阶段以 **Idea 正式闭环** 为唯一目标。
Trend、Preference Learning、PWA、移动端均不进入当前主线。

---

## 2. 阶段目标

Phase 3 需要完成的不是单纯的 `ideas` 数据表或 CRUD 页面，而是一个最小但真实可运行的 Idea 产品闭环：

1. 用户可创建 Idea
2. Idea 可来自 Note 或独立输入
3. 系统可对 Idea 执行一次最小 AI assess
4. assessment 结果结构化存储
5. Idea 状态按状态机推进
6. assessment 结果可派生下一步任务
7. 前端可展示 Idea 列表、详情、assessment 和 task 派生入口
8. 全链路保持 trace / event / structured logging / doc sync 一致

---

## 3. 当前冻结边界

### 3.1 Phase 3 必做

1. `ideas` 领域模型落地
2. Idea 生命周期落地：
  - `CAPTURED`
  - `ASSESSED`
  - `PLANNED`
  - `IN_PROGRESS`
  - `ARCHIVED`
3. 创建 Idea：
  - `FROM_NOTE`
  - `INDEPENDENT`
4. 最小 Idea assess AI 切片
5. Idea -> Task 派生
6. Idea 工作台最小可用
7. trace / log / event / docs 对齐

### 3.2 Phase 3 可预留但不做正式闭环

1. Trend source registry / Trend Inbox
2. `user_preference_profiles` 正式读写闭环
3. 更复杂的 prompt registry / model routing
4. Idea 自动优先级学习
5. Idea 复杂协作流程
6. 高保真 UI 重构

### 3.3 Phase 3 明确不做

1. Trend 正式主流程
2. Preference 正式画像重算器
3. 移动端
4. 导出中心
5. 任意自由抓取
6. 多 provider 复杂 AI 平台化

---

## 4. Phase 3 切片计划

## Step 3.1：Idea schema / contract 基线

### 目标
先冻结 Phase 3 的后端合同和状态机基础，防止后续 assess / task / web 漂移。

### 交付
1. `ideas` 表 / migration
2. enum / state constants
3. entity / model / repository
4. request / response DTO
5. 基础 API contract
6. 文档同步到 Phase 3 语义

### 最小验收
- `ideas` 可被持久化
- Idea 状态枚举可统一使用
- API contract 与 persistence 对齐
- 文档明确记录 Phase 3 已开始

### 风险
- 若字段设计过早泛化，会影响后面 assess 结构
- 若状态机没冻结，前后端会漂移

### 建议后续
进入 Step 3.2

---

## Step 3.2：Idea create 最小闭环

### 目标
让 Idea 能真正被创建，而不是只有 schema。

### 范围
支持两种来源：
1. `FROM_NOTE`
2. `INDEPENDENT`

### 交付
1. `POST /api/v1/ideas`
2. 创建 service / command handler
3. 基础校验
4. 必要的日志与 trace
5. 最小前端创建入口（如果当前阶段已接 Web）

### 最小验收
- 能创建独立 Idea
- 能基于 Note 创建 Idea
- 初始状态为 `CAPTURED`
- 写入必要 trace/log

### deferred
- 高级字段自动补全
- 更复杂来源归因
- 智能去重

### 建议后续
进入 Step 3.3

---

## Step 3.3：Idea assess 最小 AI 切片（Phase 3 关键步骤）

### 目标
让 Idea 从“记录点子”升级为“受控评估对象”。

### 这是 Phase 3 的关键要求
若缺失本步骤，Phase 3 不能视为真正完成最小闭环。

### 交付
1. `POST /api/v1/ideas/{id}/assess`
2. `IdeaAssessmentService`
3. `IdeaAgent` interface（可先最小实现）
4. assessment request / result contract
5. `assessment_result` 落库
6. 状态推进：
  - `CAPTURED -> ASSESSED`
7. `agent_traces` 记录
8. 结构化日志
9. 至少一个相关事件记录

### 最小 assessment 输出
必须至少包含：
- `problem_statement`
- `target_user`
- `core_hypothesis`
- `mvp_validation_path`
- `next_actions`

### 可以接受的实现方式
- 单 provider
- 单 prompt
- stub / mock adapter
- 同步调用

### 不可接受的实现方式
- controller 中直接调用模型
- assessment 只返回自然语言长文本
- 没有落库
- 不推进状态
- 没有 trace/log

### 最小验收
- assess 接口真实可调用
- 能返回结构化 assessment
- `ideas.assessment_result` 被写入
- Idea 状态进入 `ASSESSED`
- trace/log 可查

### deferred
- 多模型路由
- assessment scoring
- prompt registry 平台化
- 更复杂评估模板

### 建议后续
进入 Step 3.4

---

## Step 3.4：Idea -> Task 派生

### 目标
把 assessment 的结果推进到行动层。

### 交付
1. 从 assessment 中生成 1~N 个 `SYSTEM` tasks
2. Task 与 Idea 正确关联
3. 需要时更新 Idea 状态为 `PLANNED`
4. Today / Upcoming 可看到这些任务

### 最小验收
- 至少能从一个 assessed idea 派生任务
- 任务具备基础标题 / 说明 / 关联 id
- Today / Upcoming 能看见任务
- 任务链路有 trace / event / logs

### deferred
- 自动批量拆分
- 智能优先级
- 复杂任务模板

### 建议后续
进入 Step 3.5

---

## Step 3.5：Idea Web 工作台

### 目标
让 Phase 3 在前端可见、可演示、可验收。

### 交付
1. Idea List
2. Idea Detail
3. Assess 按钮
4. Assessment Result 展示
5. Generate Task / View Tasks 入口
6. 加载 / 空 / 错误态

### 最小验收
- 可以浏览 Idea 列表
- 可以查看详情
- 可以触发 assess
- 可以看到 assessment 结果
- 可以看到派生任务入口或结果

### deferred
- Kanban / Pipeline
- 大规模视觉重构
- 高级筛选排序

### 建议后续
进入 Step 3.6

---

## Step 3.6：Phase 3 文档与治理收口

### 目标
确保实现、文档、仓库规范一致，不留明显漂移。

### 交付
1. 更新 `docs/codex/Documentation.md`
2. 更新 milestone 状态
3. 记录 deferred backlog
4. 标记已完成与未完成边界
5. 校验 `AGENTS.md` / `Implement.md` / skill 是否仍与当前阶段一致

### 最小验收
- 文档能准确描述当前 Phase 3 实现范围
- 未完成内容被显式记录
- 没有把 Phase 4/5 能力误写成已实现

---

## 5. 阶段完成定义

仅当以下条件同时满足，才可以说 Phase 3 达到最小闭环：

1. Idea 可创建
2. Idea 可 assess
3. assessment 结果结构化落库
4. Idea 状态能从 `CAPTURED` 进入 `ASSESSED`
5. assessment 能派生至少一个 task 或明确下一步动作
6. 前端能展示 Idea 与 assessment
7. trace / log / event / docs 已同步

只完成 CRUD、表结构或页面壳子，不算 Phase 3 闭环。

---

## 6. Deferred Backlog 记录要求

凡是为了最小闭环暂时跳过的能力，必须在 `Documentation.md` 中记录：

1. 跳过了什么
2. 为什么现在不做
3. 预期在哪个 Phase 补回
4. 当前造成什么限制

阶段性跳过，不代表永久删除。
