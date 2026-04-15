# Prompt
# docs/codex/Prompt.md

## 1. 文件目的

本文件定义 Codex / coding agent 在 NoteOps 仓库中执行 **Phase 5** 任务时的直接提示基线。

当前唯一开发目标为：

**Phase 5：Preference Signal Layer + PWA Limited Offline Review**

注意：
- 当前阶段不是继续堆 Trend 细节，也不是直接做移动端。
- 当前阶段不是做一个复杂推荐系统，也不是做“全站离线”。
- 当前阶段必须完成两个受控闭环：
    1. `user_action_events -> user_preference_profiles -> context injection`
    2. `cached review/task/note summary -> offline actions -> sync/actions`

---

## 2. Phase 5 冻结目标

你当前正在推进的是 **NoteOps Agent 的 Phase 5**。
本阶段必须围绕以下两条主线展开，而不是发散到导出中心、移动端、任意抓取或复杂个性化平台。

本阶段正式目标：

1. 补齐 `user_action_events` 的关键行为采集
2. 落地 `user_preference_profiles` 的最小读模型
3. 提供最小 preference recompute / refresh 机制
4. 将 preference 以 **建议层 context injection** 的形式注入现有能力
5. 提供 PWA 基础壳与有限缓存能力
6. 支持离线 review 最小闭环
7. 支持 `POST /api/v1/sync/actions` 回传离线动作
8. 保持 trace / event / structured logging / documentation 与当前仓库治理方式一致

---

## 3. 本阶段绝对不要做的事

以下能力本阶段可预留，但不做正式闭环：

1. 复杂推荐系统 / 复杂长期个性化排序
2. prompt 自由自改写 / 自我演化系统
3. 多 provider 复杂模型编排平台
4. 原生 Android / iOS
5. 导出中心
6. 任意网站自由抓取
7. 全量离线能力
8. 离线外部检索、离线 URL 抽取、离线 Trend 抓取、离线深度 LLM 分析
9. Preference 直接静默改写最终内容输出
10. 为未来所有偏好维度一次性铺大量抽象层

---

## 4. 实施总原则

### 4.1 分两段推进

Phase 5 先做：
- Preference Signal Layer

再做：
- PWA Limited Offline Review + Sync

不要一开始同时重做前端壳、离线缓存、偏好学习、复杂推荐。

### 4.2 偏好学习原则

系统先学习：
- 用户关心什么

再学习：
- 用户怎么表达

当前阶段优先做 `interest_profile`，后置复杂 `output_style_profile`。

### 4.3 建议层注入原则

Preference 的产物默认进入 **建议层 / context injection**，而不是静默覆盖最终结果。

可以影响：
- Trend suggested action
- Trend/ Search 排序
- Idea / Task 建议优先级

不应直接影响：
- 原始正文
- 已确认的最终状态
- 用户未同意的关键内容改写

### 4.4 有限离线原则

当前阶段只支持有限离线：
- 查看已缓存 Note 摘要
- 查看 Today Review
- 完成基础 review
- 记录 mastery / self recall / 简短备注
- 记录轻量 task 动作（如仓库当前已冻结允许）
- 本地保存 pending actions 并联网回传

当前阶段明确不支持离线：
- 外部检索
- URL 抽取
- Trend 抓取
- 深度 LLM 分析
- proposal apply / rollback

### 4.5 治理一致性原则

Preference 与 PWA 都不是例外。
凡涉及：
- 用户行为事件记录
- profile 重算
- preference 注入
- 离线动作回放
- sync 合并

都必须同步考虑：
- `agent_traces`
- `tool_invocation_logs`（如涉及）
- `user_action_events`
- 结构化日志
- `docs/codex/Documentation.md`

---

## 5. Phase 5 推荐切片顺序

### Step 5.1
补齐 `user_action_events` 关键事件链路：
- trend ignored / saved / promoted
- review completed / partial / not_started
- task completed / skipped
- proposal applied / rejected / rolled_back

### Step 5.2
`user_preference_profiles` 最小结构与读写基线：
- `interest_profile`
- 最小 repository / DTO / contract

### Step 5.3
Preference recompute / refresh 最小闭环：
- 从事件聚合生成 `interest_profile`
- 手动触发或最小定时触发

### Step 5.4
Preference context injection：
- Trend suggested action / 排序增强
- Search related / external suggestion 排序增强
- 不静默改写最终结果

### Step 5.5
PWA 基础壳：
- manifest
- service worker
- 核心静态资源缓存
- review / note summary / tasks 的最小缓存策略

### Step 5.6
离线 review + `sync/actions` 最小闭环：
- 离线完成 review
- 记录 pending actions
- 联网回传并做幂等合并

### Step 5.7
Phase 5 文档与治理收口：
- 文档同步
- 风险记录
- deferred backlog 更新

---

## 6. Preference 最小闭环的硬要求

这是 Phase 5 的第一主线，不允许遗漏。

最小 Preference 闭环必须满足：

1. 有正式 `user_action_events` 记录链路
2. 有正式 `user_preference_profiles` 读模型或持久化对象
3. 至少先支持 `interest_profile`
4. 至少一种 recompute / refresh 机制真实可调用
5. 至少一个现有能力能读取 preference 并作为建议层输入
6. preference 不得静默覆盖最终业务对象
7. 关键链路必须写 trace / logs / 文档

---

## 7. PWA / Offline 最小闭环的硬要求

这是 Phase 5 的第二主线，不允许做成“仅有壳子”。

最小 PWA / Offline 闭环必须满足：

1. 前端具备 PWA 基础壳（manifest + service worker）
2. 至少一条真实数据链路可离线使用
3. 离线 review 动作可本地记录
4. 有正式 `POST /api/v1/sync/actions` 合同与回放路径
5. 服务端对离线动作至少具备幂等与基本校验
6. 明确区分允许离线与禁止离线的动作
7. 核心同步链路有结构化日志

---

## 8. `interest_profile` 建议结构

建议最小结构如下：

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

允许后续扩展，但当前阶段不要为了“以后可能会加”引入复杂画像协议。

---

## 9. 允许的简化

当前阶段允许简化：

1. preference 先只做 `interest_profile`
2. recompute 先做手动触发或简单 job
3. 先只注入 Trend / Search，不必一次影响所有 Agent
4. PWA 先覆盖 Review 主链路
5. 缓存策略先做最小集，不追求全站离线
6. sync 先只支持最少动作类型

---

## 10. 不允许的简化

以下简化不可接受：

1. 只有 `user_preference_profiles` 表，没有真实 recompute
2. 只有事件表，没有任何 profile 产物
3. profile 生成后没有任何地方读取
4. 只有 PWA 壳，没有真实离线动作链路
5. 本地缓存了数据，但没有 pending actions / sync 回传
6. sync 只有接口壳，没有真正合并或校验
7. 离线边界未定义，什么都能离线提交
8. 直接把 preference 结果静默写死到最终内容

---

## 11. 每次任务完成后的输出要求

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
