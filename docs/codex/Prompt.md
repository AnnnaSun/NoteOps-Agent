# Prompt.md
# NoteOps-Agent Phase 3 执行提示（Idea 正式闭环）

## 1. 本轮目标

当前仓库已完成 Phase 2 最小闭环。
在正式推进 Idea 主线前，先完成一个 **Phase 3 前置补丁：Capture AI 最小闭环**，用于把当前系统推进到“至少已有一条真实可演示的 AI 主链路”。
这一步只允许落在 Capture 的 `ANALYZING` 阶段，不等于开始 Search / Review / Task / Idea / Trend 的全面 AI 化。

本轮进入 **Phase 3：Idea 正式闭环**，目标是把 Note / Search 产出的想法沉淀为可评估、可推进、可转任务的 Idea 工作流。

本轮**只做 Phase 3 的最小闭环**，不要提前进入 Trend 正式闭环，不要把 Preference Learning 做成完整画像系统，不要为了未来铺过多抽象层。

## 2. Phase 3 必做范围

1. **Idea 实体正式落地**
    - 支持 `CAPTURED / ASSESSED / PLANNED / IN_PROGRESS / ARCHIVED`
    - 支持 `source_note_id`
    - 支持 `assessment_result`
    - 所有核心表与接口保留 `user_id`

2. **Idea 来源**
    - 支持从 `Note` 生成 Idea
    - 支持独立创建 Idea
    - 可以为未来 `FROM_SEARCH / FROM_TREND_CANDIDATE` 预留枚举或字段，但不要做正式闭环

3. **Idea Assess**
    - 评估结果至少包含：
        - 问题定义
        - 目标用户
        - 核心假设
        - MVP 验证路径
    - 不要求现在实现复杂评分引擎

4. **Idea -> Task**
    - 支持基于 Idea 一键生成 system task
    - 生成后的 task 能进入 Today / Upcoming 视图
    - task 与 `linked_entity_type = IDEA`、`linked_entity_id` 对齐

5. **Idea UI 最小工作台**
    - 至少具备：
        - Idea List
        - Idea Detail
        - Assess 入口
        - Generate Task 入口
    - 页面必须连真实接口，不允许纯前端假数据冒充完成

6. **治理链路**
    - Idea 关键字段更新、assessment 写回、任务生成，必须写 trace / event
    - 关键更新默认走 proposal / trace 语义，不要让 Idea 成为绕开治理的例外
    - 关键链路必须补齐结构化日志

## 3. 本轮明确不做

1. Trend 正式抓取、排序、趋势池闭环
2. user_preference_profiles 完整重算与自动学习管线
3. Idea 复杂评分系统（价值分、风险分、置信度）
4. Idea Kanban / Pipeline 高级视图
5. 周/月 Calendar
6. 多 provider 外部实时接入
7. 大规模通用化 Agent framework

## 4. 默认实施顺序

1. schema / enum / state machine
2. domain model / repository / migration
3. DTO / API contract
4. service / orchestration
5. trace / event / proposal / log
6. frontend API client
7. Idea List / Detail / Assess / Generate Task 页面
8. 最小验证与文档同步

不要反过来先堆 UI，再回头补后端契约。

## 5. 领域与状态约束

1. **Idea 生命周期**
    - `CAPTURED -> ASSESSED -> PLANNED -> IN_PROGRESS -> ARCHIVED`
    - 可支持 `ARCHIVED -> PLANNED` 重新开启

2. **Note 仍是第一公民**
    - Idea 不是脱离 Note 的独立系统
    - 即便支持独立创建，也要保留与 Note / Search / Task 的主链路关系

3. **Proposal / Trace 不可绕开**
    - Idea assessment_result、title、relation、task planning 等关键变更，要么经 proposal，要么至少保留 trace + event + before/after 摘要
    - 不允许关键变更完全静默写库

4. **Task 继续沿用现有轻量状态**
    - `TODO / IN_PROGRESS / DONE / SKIPPED / CANCELLED`

## 6. 日志与可观测性要求

以下场景必须补结构化日志：

- 创建 Idea
- 从 Note 派生 Idea
- Assess 开始 / 成功 / 失败
- Generate Task 开始 / 成功 / 失败
- Idea 状态迁移
- Proposal apply / reject / rollback（如果本轮涉及）
- 写入 trace / event / user_action_event

日志至少包含：

- `trace_id`
- `user_id`
- `idea_id`（如适用）
- `action`
- `result`
- `duration_ms`（如适用）
- `error_code` / `error_message`（失败时）

## 7. 每一步输出要求

### 本次完成
- 本步支持了什么真实行为

### 修改文件
- 改了哪些关键文件
- 为什么改

### 验证结果
- 跑了哪些命令 / 测试
- 通过 / 失败情况
- 哪些还没验证

### 风险与下一步
- 当前最主要缺口
- 下一个最小切片是什么

## 8. 完成定义

只有同时满足以下条件，Phase 3 才能标记“已完成最小闭环”：

1. 可以创建 Idea
2. 可以从 Note 派生 Idea
3. 可以对 Idea 做最小 Assess
4. 可以从 Idea 生成真实 system task
5. Today / Upcoming 能看到生成后的任务
6. 关键状态迁移有 trace / event / log
7. 前端工作台走真实接口
8. Documentation.md 已同步当前真实实现

## 9. Deferred Backlog 记录要求

任何为了最小闭环暂时跳过的能力，必须显式记录到 deferred backlog，例如：

- Idea scoring
- Trend candidate 接入
- Preference profile 重算
- Kanban / pipeline
- richer assessment templates

**阶段性跳过 ≠ 永久删除。**
