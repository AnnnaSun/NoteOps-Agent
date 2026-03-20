# Plan.md
# NoteOps-Agent Phase 3 实施计划（Idea 正式闭环）

## 0. 阶段目标

Phase 3 的目标不是做 Trend，而是把 Phase 2 已有的 Note / Search / Review / Task 基础继续向前推进，完成 **Idea 正式闭环**：

- 想法能被沉淀为 `Idea`
- Idea 能被评估
- Idea 能被推进为 Task
- 关键动作具备 trace / proposal / event / log
- Web 端具备最小可操作工作台

## 1. 本阶段范围

### 1.1 必做
1. Idea schema、枚举、状态机对齐
2. Idea 创建
3. Note -> Idea 派生
4. Idea Assess
5. Idea -> Task
6. Idea List / Detail / Assess / Generate Task UI
7. trace / event / user_action_event / structured log 补齐
8. 文档同步

### 1.2 不做
1. Trend 正式闭环
2. Preference 正式学习闭环
3. 复杂评分系统
4. Kanban / board
5. 高级推荐排序
6. 周/月日历
7. 多来源实时抓取

### 1.3 只预留
1. `FROM_SEARCH`
2. `FROM_TREND_CANDIDATE`
3. `user_preference_profiles` 读模型或占位 service
4. richer assessment templates

## 2. Step 切分

## 前置补丁：Capture AI 最小闭环（已完成）
### 目标
在正式进入 Idea 主线前，先把 `POST /api/v1/captures` 与 `GET /api/v1/captures/{capture_job_id}` 升级为真实 AI 主链路，而不是 placeholder summary。

### 实际已完成
- 支持 `TEXT / URL`
- CaptureJob 状态机固定为：
    - `RECEIVED`
    - `EXTRACTING`
    - `ANALYZING`
    - `CONSOLIDATING`
    - `COMPLETED`
    - `FAILED`
- `TEXT` 走原文规整提取，`URL` 走最小 HTTP 快照提取
- `ANALYZING` 阶段支持 `DEEPSEEK / KIMI / GEMINI / OLLAMA` 四个 provider 的真实结构化 JSON 调用
- provider/router 已抽到共享 `application.ai` 平台，当前只有 `capture-analysis` 接入，未顺势扩到 `note/task/search/review`
- 服务端对 `CaptureAnalysisResult` 做强校验
- consolidate 默认新建 Note，原始输入落 `CAPTURE_RAW`，AI 结果仅写 Note 当前解释层与 `analysis_result`
- 写入 `agent_traces`、`tool_invocation_logs`、`user_action_events`、结构化日志

### 明确不扩张
- 不做旧 Note 匹配
- 不做 `UPDATE / APPEND / CONFLICT`
- 不把 Proposal 设为 Capture 主入口
- 不扩 Search / Review / Task / Idea / Trend 的 AI 主链
- 不引入异步队列、多模型路由平台、prompt registry

### 下一正式里程碑
- 完成前置补丁后，继续按 Idea 主线推进，从 `Step 3.1` / `Step 3.2` 继续，不把 Capture 补丁误判为 Phase 3 全量开始

## Step 3.1：锁定 Idea 领域模型与状态机
### 目标
让 schema / entity / enum / DTO 对齐 Phase 3 的 Idea 语义。

### 需要完成
- 检查是否已有 `ideas` 表或对应迁移
- 对齐状态：
    - `CAPTURED`
    - `ASSESSED`
    - `PLANNED`
    - `IN_PROGRESS`
    - `ARCHIVED`
- 对齐核心字段：
    - `id`
    - `user_id`
    - `source_note_id`
    - `title`
    - `status`
    - `assessment_result`
    - `created_at`
    - `updated_at`
- 如现有 schema 不完整，补 migration
- 同步 Java 枚举 / entity / repository / DTO

### 前置准备
- 阅读现有 migration、entity、repository、controller、DTO
- 查清 Phase 2 是否已有占位实现

### 验收点
- 后端可构建
- 枚举与表字段无漂移
- 文档里能明确当前 Idea 模型

## Step 3.2：创建 Idea 与从 Note 派生 Idea
### 目标
打通 Phase 3 的最基本输入来源。

### 需要完成
- `POST /api/ideas`
- `POST /api/notes/{noteId}/ideas` 或同等语义接口
- 支持两种来源：
    - 独立创建
    - 从 Note 派生
- 写入 trace / user_action_event
- 补齐结构化日志

### 验收点
- 能创建独立 Idea
- 能从指定 Note 派生 Idea
- 接口返回 envelope 一致
- 日志中能定位 `idea_id` / `note_id` / `trace_id`

## Step 3.3：Idea Detail 与 List 查询
### 目标
给前端工作台最小读取能力。

### 需要完成
- `GET /api/ideas`
- `GET /api/ideas/{id}`
- 支持基础过滤：
    - status
    - source_note_id（可选）
- 前端 API client 接通
- 最小列表 / 详情页展示真实数据

### 验收点
- 页面能列出 Idea
- 能进入详情页
- 加载 / 空态 / 错误态明确

## Step 3.4：Idea Assess 最小闭环
### 目标
让 Idea 从“被记录”进入“被评估”。

### 需要完成
- `POST /api/ideas/{id}/assess`
- assessment_result 最少包含：
    - `problem_definition`
    - `target_user`
    - `core_hypothesis`
    - `mvp_validation_path`
- 状态从 `CAPTURED -> ASSESSED`
- 写 trace / event / user_action_event
- 关键写入保留 before / after 摘要
- 补齐结构化日志

### 验收点
- 接口可成功写入 assessment_result
- 状态按预期迁移
- Detail 页能展示 assessment 内容
- 失败场景可从日志定位

## Step 3.5：Idea -> Task 最小闭环
### 目标
把 Idea 推进到执行层，而不是停留在想法展示。

### 需要完成
- `POST /api/ideas/{id}/generate-task`
- 生成 `task_source = SYSTEM`
- 绑定：
    - `linked_entity_type = IDEA`
    - `linked_entity_id = idea.id`
- Idea 状态从 `ASSESSED -> PLANNED`
- Today / Upcoming 可见
- 写 trace / event / user_action_event
- 补齐结构化日志

### 验收点
- 成功生成真实 task
- task 可在 Today / Upcoming 中看到
- Task 与 Idea 关系可追溯
- 失败场景可从 trace 与日志回放

## Step 3.6：Idea 继续推进与归档
### 目标
把生命周期补到最小完整，不停在 PLANNED。

### 需要完成
- 至少支持：
    - `POST /api/ideas/{id}/start`：`PLANNED -> IN_PROGRESS`
    - `POST /api/ideas/{id}/archive`：`IN_PROGRESS|PLANNED|ASSESSED -> ARCHIVED`
    - 可选 `POST /api/ideas/{id}/reopen`：`ARCHIVED -> PLANNED`
- 写 trace / event / user_action_event
- 补齐结构化日志

### 验收点
- Idea 生命周期能走到归档
- 详情页可看到状态变化
- 状态机规则不混乱

## Step 3.7：治理链路补强
### 目标
让 Idea 不成为绕开治理的孤岛。

### 需要完成
- 对关键更新评估是否经 proposal 或至少保留 before / after 摘要
- 检查 trace / event / user_action_event 的一致性
- 检查日志字段最小集合
- 校验 DTO / schema / 文档是否漂移

### 验收点
- 关键动作可追溯
- 无核心链路静默写库
- 无接口与文档明显不一致

## Step 3.8：文档与阶段收口
### 目标
让仓库执行文档和真实代码对齐。

### 需要完成
- 更新 `docs/codex/Documentation.md`
- 如范围变化，更新 `AGENTS.md`
- 如执行规则变化，更新 `docs/codex/Implement.md`
- 如 phase skill 仍停留在 Phase 2，更新 skill

### 验收点
- 文档能解释当前已实现行为
- 明确 Deferred Backlog
- 下一阶段边界清楚

## 3. 验证策略

每个 Step 默认执行：

1. 相关单元 / 集成测试
2. build / type check
3. migration 验证（如果 schema 有变）
4. 手工 API 链路验证
5. 前端最小点击验证（若页面改动）

禁止声称执行了未执行的验证。

## 4. 完成标准

Phase 3 只有满足以下条件，才算“已完成最小闭环”：

1. 独立创建 Idea 成功
2. 从 Note 派生 Idea 成功
3. Idea List / Detail 可用
4. Idea Assess 成功
5. Idea -> Task 成功
6. Today / Upcoming 能看到生成任务
7. 状态至少能走到 `ARCHIVED`
8. trace / event / logs 补齐
9. 文档同步完成

## 5. Deferred Backlog

以下内容在 Phase 3 结束后必须保留为后续计划，不得遗失：

1. Trend 正式闭环
2. user_preference_profiles 正式学习与重算
3. Idea scoring / ranking
4. Trend candidate -> Idea 自动转化
5. Idea Kanban / board
6. richer assessment template
7. 推荐排序与偏好融合

阶段性跳过仅代表“后置”，不代表“删除”。
