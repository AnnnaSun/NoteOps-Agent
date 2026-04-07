# Prompt
# docs/codex/Prompt.md

## 1. 文件目的

本文件定义 Codex / coding agent 在 NoteOps 仓库中执行 **Phase 3** 任务时的直接提示基线。

当前唯一开发目标为：

**Phase 3：Idea Lifecycle / Idea Workspace / Minimal Idea AI Assess Slice**

注意：
- 当前阶段不是 Trend，不是 Preference Learning 正式闭环。
- 当前阶段也不是只做 `ideas` 表和 CRUD。
- 当前阶段必须完成 Idea 的最小产品闭环：**创建 -> 评估 -> 生成下一步动作 -> 派生 Task -> Web 可见**。

---

## 2. Phase 3 冻结目标

你当前正在推进的是 **NoteOps Agent 的 Phase 3**。
本阶段必须严格围绕 **Idea** 展开，而不是发散到 Trend、Preference、PWA 或移动端。

本阶段的正式目标：

1. 落地 `ideas` 领域模型与生命周期。
2. 支持 Idea 两种来源：
   - `FROM_NOTE`
   - `INDEPENDENT`
3. 提供最小可用的 Idea 工作台：
   - Idea List
   - Idea Detail
   - Assess 入口
   - Assessment 展示
4. 提供最小 **Idea AI assess** 切片：
   - `POST /api/v1/ideas/{id}/assess`
   - 受控调用 Idea Agent / Assessment Service
   - 输出结构化 assessment result
   - 将 Idea 从 `CAPTURED` 推进到 `ASSESSED`
5. 支持由 assessment 结果派生 1~N 个 `SYSTEM` task。
6. 保持 trace / event / structured logging / documentation 与当前仓库治理方式一致。

---

## 3. 本阶段绝对不要做的事

以下能力本阶段可预留，但不做正式闭环：

1. Trend Inbox / Trend source registry / Trend scoring
2. `user_preference_profiles` 正式重算与回写
3. Prompt 自我改写或自由演化系统
4. 复杂 Idea 打分算法、复杂商业评分器、复杂优先级学习
5. 真正成熟的多 provider AI 编排系统
6. 周/月日历重构
7. 原生移动端
8. 导出中心
9. 任意网站抓取
10. 为未来 Trend / Preference 提前铺大量抽象层

---

## 4. 实施总原则

### 4.1 最小闭环原则

每次只实现一个最小可验收切片。
不要一次把 schema、AI provider、前端、复杂治理、复杂 prompt 系统全部做完。

### 4.2 Note-first 原则

Idea 是独立实体，但仍属于 Note-first 体系：
- Idea 可以来源于 Note
- Idea 可以派生 Task
- Idea 的评估与推进不能脱离主知识链路

### 4.3 AI 受控原则

Idea 的 AI 能力必须是 **受控 assessment**，而不是自由生成器。

AI 只负责：
- 结构化理解输入
- 生成 assessment
- 给出验证路径与下一步动作建议

AI 不负责：
- 直接拍板改数据库
- 绕过状态机推进多个对象
- 静默改写 Idea 主记录核心字段
- 直接替用户创建不可追溯的结果

### 4.4 治理一致性原则

Idea assess 不是例外。
它必须延续当前仓库的治理语义：

- 可追溯
- 可验证
- 可观察
- 可记录来源
- 不静默越权

凡涉及：
- 状态推进
- 自动任务生成
- AI assessment 结果落库
- 后续建议生成

都必须同步考虑：
- `agent_traces`
- `tool_invocation_logs`
- `user_action_events`
- 结构化日志
- `docs/codex/Documentation.md`

---

## 5. Phase 3 推荐切片顺序

推荐按以下顺序推进：

### Step 3.1
`ideas` schema / enum / entity / repository / DTO / API contract

### Step 3.2
Idea create 最小闭环：
- `FROM_NOTE`
- `INDEPENDENT`

### Step 3.3
Idea assess 最小 AI 切片：
- `/api/v1/ideas/{id}/assess`
- `IdeaAssessmentService`
- `IdeaAgent` interface / stub
- `assessment_result` 落库
- 状态推进到 `ASSESSED`
- trace / log / event 补齐

### Step 3.4
Idea -> Task 派生：
- 基于 assessment 生成 `SYSTEM` task
- 进入 Today / Upcoming

### Step 3.5
Idea Web 工作台：
- List / Detail / Assess 按钮 / Assessment 展示 / Generate Tasks

### Step 3.6
Phase 3 文档与治理收口：
- 文档同步
- 风险记录
- deferred backlog 记录

---

## 6. Idea Assess 最小 AI 切片的硬要求

这是 Phase 3 的关键补丁，不允许遗漏。

最小 assess 切片必须满足：

1. 有正式接口：`POST /api/v1/ideas/{id}/assess`
2. 有正式领域服务，而不是 controller 里直接拼 prompt
3. assessment 输出必须结构化，不接受只返回一段自由文本
4. 结果至少包含：
   - `problem_statement`
   - `target_user`
   - `core_hypothesis`
   - `mvp_validation_path`
   - `next_actions`
5. 成功后 Idea 状态从 `CAPTURED` -> `ASSESSED`
6. assessment 过程必须写 trace
7. 关键链路必须写结构化日志
8. 至少写入一个与 Idea assess 相关的 `user_action_event` 或等价治理记录
9. AI provider 可先 stub / mock / minimal adapter，但 contract 必须稳定
10. 未做的增强项必须记录到 deferred backlog

---

## 7. assessment_result 建议合同

最小结构建议如下：

```json
{
  "problem_statement": "string",
  "target_user": "string",
  "core_hypothesis": "string",
  "mvp_validation_path": [
    "string"
  ],
  "next_actions": [
    "string"
  ],
  "risks": [
    "string"
  ],
  "reasoning_summary": "string"
}
```

允许后续扩展，但当前阶段不要为了“以后可能会加”引入复杂泛化协议。

---

## 8. 允许的简化

当前阶段允许简化：

1. AI provider 先走单一 provider
2. prompt 模板先写成单一模板
3. assessment 不做复杂打分
4. `next_actions` 先只生成 1~3 条
5. Idea -> Task 先做显式触发，不做完全自动批量编排
6. Web UI 先做最小可读，不做重视觉

---

## 9. 不允许的简化

以下简化不可接受：

1. 只有 `ideas` CRUD，没有 assess
2. 只有 assess 按钮，没有正式后端接口
3. assessment 结果不落库
4. 评估后不推进状态
5. 核心链路没有 trace / logs
6. 直接在 controller 写死 AI prompt 调用
7. 只做前端假数据，不打通真实合同
8. 把 Phase 4 Trend 逻辑顺手塞进来

---

## 10. 每次任务完成后的输出要求

每次完成一个切片后，必须给出：

### 本次完成
- 当前 Step / 子切片名称
- 当前新增的真实行为

### 修改文件
- 关键文件与职责

### 验证结果
- 运行了什么测试 / 构建 / 手工检查
- 哪些通过
- 哪些没跑

### 日志覆盖
- 新增了哪些关键日志点
- 哪些链路已带 `trace_id`
- 哪些失败场景可定位

### 风险与下一步
- 未覆盖边界
- deferred items
- 下一最小切片
