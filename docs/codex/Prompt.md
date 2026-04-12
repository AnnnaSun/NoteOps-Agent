# Prompt
# docs/codex/Prompt.md

## 1. 文件目的

本文件定义 Codex / coding agent 在 NoteOps 仓库中执行 **Phase 4** 任务时的直接提示基线。

当前唯一开发目标为：

**Phase 4：Trend Source Registry / Trend Inbox / Trend-to-Note-Idea Flow**

注意：
- 当前阶段不是继续扩写 Phase 3 Idea 细节，也不是正式 Preference Learning 闭环。
- 当前阶段也不是做一个通用抓取平台或任意网站爬虫系统。
- 当前阶段必须完成 Trend 的最小产品闭环：
  **注册来源 -> 拉取候选 -> 结构化分析 -> 进入 Trend Inbox -> 转为 Note / 提升为 Idea**。

---

## 2. Phase 4 冻结目标

你当前正在推进的是 **NoteOps Agent 的 Phase 4**。
本阶段必须严格围绕 **Trend** 展开，而不是发散到 Preference、PWA、移动端或复杂推荐系统。

本阶段的正式目标：

1. 落地 Trend 的最小领域模型与接入基线。
2. 提供最小 Trend Source Registry。
3. 提供系统默认 Trend Plan：
    - 来源：`HN`、`GITHUB`
    - 频率：固定调度（可先 daily）
    - 输出：Trend Inbox 候选
4. 提供最小 Trend ingest 闭环：
    - 拉取候选
    - 归一化
    - 去重
    - 写入 `trend_items`
5. 提供最小 Trend AI 分析切片：
    - 简练摘要
    - 标签/主题提取
    - `suggested_action` 建议
    - `note_worthy` / `idea_worthy` 判断
6. 提供 Trend Inbox：
    - `IGNORE`
    - `SAVE_AS_NOTE`
    - `PROMOTE_TO_IDEA`
7. 提供转化闭环：
    - Trend -> Note
    - Trend -> Idea
    - 转 Idea 后复用既有 Idea assess 能力
8. 保持 trace / event / structured logging / documentation 与当前仓库治理方式一致。

---

## 3. 本阶段绝对不要做的事

以下能力本阶段可预留，但不做正式闭环：

1. 任意网站自由抓取
2. 复杂多来源 connector 平台化
3. 正式 `user_preference_profiles` 重算与自动影响排序
4. 自动静默创建大量 Note / Idea
5. 多 provider 复杂 AI 编排平台
6. 复杂个性化推荐算法
7. 周/月日历重构
8. 原生移动端
9. 导出中心
10. Prompt 自我演化系统

---

## 4. 实施总原则

### 4.1 最小闭环原则

每次只实现一个最小可验收切片。
不要一次把 registry、scheduler、connector、AI、前端、偏好学习全部做完。

### 4.2 Note-first 原则

Trend 本身不是最终价值对象。
Trend 的价值在于把高价值外部输入带进主知识链路：
- 可转为 Note
- 可提升为 Idea
- 后续再进入 Task / Review / Preference 输入

### 4.3 AI 受控原则

Trend 的 AI 不是网页抓取器，而是 **结构化分析与转化建议器**。

AI 只负责：
- 压缩摘要
- 提取主题与标签
- 解释“为什么值得关注”
- 判断更适合转 Note 还是转 Idea
- 给出 `suggested_action`

AI 不负责：
- 直接抓取网页
- 越权写数据库主对象
- 静默批量创建 Note / Idea
- 绕过用户决策直接推进高影响动作

### 4.4 治理一致性原则

Trend 不是例外。
它必须延续当前仓库治理语义：
- 可追溯
- 可验证
- 可观察
- 可记录来源
- 不静默越权

凡涉及：
- 候选入库
- 趋势分析
- 转 Note / 转 Idea
- 用户忽略 / 收藏 / 保存动作

都必须同步考虑：
- `agent_traces`
- `tool_invocation_logs`
- `user_action_events`
- 结构化日志
- `docs/codex/Documentation.md`

---

## 5. Phase 4 推荐切片顺序

推荐按以下顺序推进：

### Step 4.1
Trend schema / enum / entity / repository / DTO / API contract 基线

### Step 4.2
Trend Source Registry 最小版：
- Source type 定义
- Registry interface
- 默认 `HN` / `GITHUB` source registration
- Default Trend Plan config

### Step 4.3
Trend ingest 最小闭环：
- 拉取候选
- 归一化
- 去重
- 入库 `trend_items`

### Step 4.4
Trend AI 分析最小切片：
- summary
- topic/tags
- `note_worthy`
- `idea_worthy`
- `suggested_action`
- 结构化 analysis payload 落库

### Step 4.5
Trend Inbox：
- List
- 过滤/排序（最小）
- IGNORE / SAVE_AS_NOTE / PROMOTE_TO_IDEA

### Step 4.6
Trend -> Note / Idea 转化：
- 生成 Note
- 生成 Idea
- 保留来源链
- Trend -> Idea 后允许复用既有 assess 流

### Step 4.7
Phase 4 文档与治理收口：
- 文档同步
- 风险记录
- deferred backlog 记录

---

## 6. 默认 Trend Plan 的硬要求

这是当前阶段的关键交付之一，不允许遗漏。

默认 Trend Plan 至少必须满足：

1. 系统内置一个默认计划，而不是只有手动导入
2. 默认来源至少包含：
    - `HN`
    - `GITHUB`
3. 默认抓取结果不是直接创建 Note / Idea，而是先进入 Trend Inbox
4. 默认计划允许配置：
    - source list
    - fetch limit
    - schedule
    - keyword bias（可最小实现）
5. 默认行为：
    - `auto_ingest = true`
    - `auto_convert = false`
6. Trend 候选必须保留来源信息与原始链接
7. 转化动作必须由用户触发或显式确认，不得默认静默执行

建议最小配置如下：

```json
{
  "plan_key": "default_ai_engineering_trends",
  "enabled": true,
  "sources": ["HN", "GITHUB"],
  "fetch_limit_per_source": 5,
  "schedule": "DAILY",
  "keyword_bias": ["agent", "llm", "memory", "retrieval", "tooling", "coding"],
  "auto_ingest": true,
  "auto_convert": false
}
```

允许后续扩展，但当前阶段不要为了“以后可能会有用户自定义平台”提前过度设计。

---

## 7. Trend AI 分析合同建议

最小结构建议如下：

```json
{
  "summary": "string",
  "why_it_matters": "string",
  "topic_tags": ["string"],
  "signal_type": "string",
  "note_worthy": true,
  "idea_worthy": false,
  "suggested_action": "SAVE_AS_NOTE",
  "reasoning_summary": "string"
}
```

说明：
- 当前阶段不要求复杂个性化排序
- 当前阶段不要求成熟评分系统
- 当前阶段优先保证结构稳定、可展示、可落库

---

## 8. 允许的简化

当前阶段允许简化：

1. 先只接 1 个 mock + 1 个最小真实 provider 适配层
2. HN / GitHub 的拉取可以先走最小公共接口，不做通用 connector 大平台
3. 先按固定规则做基础 score / rank
4. `suggested_action` 先限定为：
    - `IGNORE`
    - `SAVE_AS_NOTE`
    - `PROMOTE_TO_IDEA`
5. 转 Idea 后复用现有 assess，不在 Trend 阶段重复实现 Idea 分析
6. Web UI 先做最小可读，不做重视觉

---

## 9. 不允许的简化

以下简化不可接受：

1. 只有 `trend_items` 表，没有真实 ingest
2. 只有列表页，没有来源入库
3. 没有 Trend Inbox，只是后端写表
4. 没有转 Note / 转 Idea 的真实后端路径
5. 核心链路没有 trace / logs
6. 在 controller 里直接写死抓取与模型调用逻辑
7. 静默自动创建 Note / Idea 而不记录来源与事件
8. 把 Preference 正式画像或复杂推荐顺手塞进来

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
