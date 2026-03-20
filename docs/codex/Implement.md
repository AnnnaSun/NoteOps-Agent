# Implement
# docs/codex/Implement.md

## 1. 文件用途

本文件定义 Codex 在 NoteOps 仓库内执行任务时的默认工作方式。  
目标是减少发散、减少误改、减少“看起来做了很多但主线没推进”的情况。

当前默认服务于 **Phase 3：Idea Lifecycle / Idea Workspace**。

---

## 2. 默认执行流程

每次执行具体任务时，按下面顺序进行：

1. 阅读 `AGENTS.md`
2. 阅读 `docs/codex/Prompt.md`
3. 阅读 `docs/codex/Plan.md`
4. 阅读 `docs/codex/Implement.md`
5. 确定当前任务所属 Phase 3 子步骤
6. 只实现该子步骤的最小闭环
7. 跑最小验证
8. 需要时更新 `docs/codex/Documentation.md`
9. 输出变更摘要、验证结果、未覆盖风险与 deferred items

---

## 3. 如何判断“只做最小闭环”

符合以下条件，才算最小闭环：

1. 该任务能单独验证
2. 该任务结束后，仓库状态比之前更完整，而不是更分散
3. 不依赖未来阶段大量未实现能力
4. 修改文件数量可控
5. 该任务能被明确验收
6. 若跳过能力，已记录进 deferred backlog

不符合以下情况：

- 为了“以后可能会用”提前铺很多抽象层
- 把 Phase 4/5 的逻辑塞进 Phase 3
- 一次改 schema、API、页面、算法、移动端预研全部内容
- 大量生成占位文件却没有跑通主链路

---

## 4. 读取与修改策略

### 4.1 先读后改

在真正修改前，必须先检查：

- 当前子步骤相关的已有代码
- 相关 DTO / entity / migration / controller / service
- 相关文档
- 是否已有测试

### 4.2 局部修改优先

优先：
- 小范围增量修改
- 沿用当前项目已存在模式
- 让每次 diff 易于 review

避免：
- 纯粹为了“看起来整洁”而大范围移动文件
- 把旧逻辑全部推翻重写
- 同时改太多层但没有验证

---

## 5. 当前阶段的具体实现偏好

### 5.1 总优先级

Phase 3 默认按以下顺序推进：

1. schema / state machine / enum 正确性
2. API contract 与 DTO 一致性
3. Idea create / assess / state transition 主链路可用
4. Idea 与 Task 派生关系可用
5. Idea List / Detail / Assess 主路径可用
6. Proposal / Event / Trace / Logs 治理链路补齐
7. Web 页面接真实接口
8. 复杂评分、Trend 集成、Preference 学习、视觉细节后置

### 5.2 后端偏好

优先保证：
1. 表结构准确
2. 状态机语义准确
3. API 契约与 envelope 一致
4. trace / proposal / event 能打通最小治理链路
5. 关键链路具备结构化日志，能够支持监控、排障与调用链定位

关键日志默认必须覆盖：
- controller 请求入口
- service 核心命令入口与出口
- idea create
- idea assess
- idea status transition
- idea task generation
- proposal apply / rollback
- 外部调用开始 / 成功 / 失败（如 assess 中存在）
- task create / complete / skip / reschedule

日志至少包含：
- `trace_id`
- `user_id`
- `action`
- 关键业务 id
- `result`
- `duration_ms`（如适用）
- `error_code` / `error_message`（失败时）

可以暂时简化：
- 复杂 Idea 打分算法
- 复杂市场评估与竞品分析
- Trend / Preference 正式接入质量
- UI 呈现细节
- 高级优化

不可以后置到未来阶段再补的内容：
- 关键链路日志
- 可关联的 trace 信息
- 核心失败分支日志
- Idea 生命周期状态推进的最小验证

### 5.3 前端偏好

优先：
- 页面能连上真实接口
- Idea create / list / detail / assess 主路径能跑通
- 状态边界清楚（加载、空、错误、成功）
- Idea 详情中能清楚看到 assessment 与派生 task

不优先：
- 复杂视觉效果
- 完整组件系统
- 动画、主题、多端适配细节
- Kanban / 拖拽 / 高级 pipeline 视图

---

## 6. 状态机与枚举处理规则

凡是涉及状态机或枚举值，必须显式同步以下位置：

1. 数据库字段约束 / 枚举定义
2. 后端领域模型
3. DTO / API 请求响应
4. 相关服务逻辑
5. 日志 / trace / user action event
6. 相关文档

不要出现“数据库是一个枚举，Java 里是另一个字符串集合，前端又写一套”的漂移。

---

## 7. 何时必须同步文档

出现以下任一情况，执行结束前必须更新文档：

- 表结构改动
- 接口路径或字段改动
- 状态机改动
- 目录结构改动
- 当前已完成子步骤变更
- 已知风险新增或消除
- 新增 deferred item 或移除 deferred item

最少更新：
- `docs/codex/Documentation.md`

必要时再更新：
- README
- schema / API 补充说明
- `docs/codex/Plan.md`

---

## 8. 验证策略

### 8.1 默认验证顺序

1. 最窄范围单元测试 / 集成测试
2. 构建 / 类型检查
3. 必要时的数据库迁移验证
4. 必要时的手工链路检查

### 8.2 验证输出要求

必须明确写出：

- 跑了什么
- 结果如何
- 哪些没跑
- 还剩什么风险

禁止用“理论上可以”“按理说没问题”代替验证结果。

---

## 9. Proposal / 原始内容特殊规则

这是 NoteOps 当前最容易被做错的地方，必须单列。

### 9.1 原始内容层
以下内容默认只追加，不静默覆盖：

- capture 原文
- source snapshot
- evidence 原文
- transcript 原文

### 9.2 proposal 作用层
Proposal 只允许作用于：

- `INTERPRETATION`
- `METADATA`
- `RELATION`

禁止把 proposal 设计成“直接改写原始 note_contents 正文”。

### 9.3 Idea 特殊规则
Idea 的 assess / edit / promote 流程中：
- 允许生成 assessment 结果
- 允许更新 Idea 当前状态
- 允许派生 task
- 不允许绕开 trace / event / proposal 治理链路静默改动关键字段

---

## 10. Idea / Task / Workspace 特殊规则

### 10.1 Idea
不要把 Idea 简化成：
- 只有标题和备注的临时草稿
- 没有状态机的轻量卡片
- assess 后不产生任何可执行结果

必须体现：
- `status`
- `assessment_result`
- `FROM_NOTE` 与独立创建两种来源
- assess 后可产生 `next_actions`
- 需要时可派生 task

### 10.2 Task
不要把 Phase 3 的 task 派生做成纯前端假数据。  
Phase 3 中 Idea 派生 task 至少要支持：

- 创建 system task
- 与 idea 建立可追溯关联
- 进入 Today / Upcoming
- 完成 / 跳过后能回写状态或事件

### 10.3 Workspace
Idea Workspace 不是简单列表页。至少要保证：

- Idea List 与 Idea Detail 都可用
- 详情中 assessment 结果可见
- 状态流转入口清晰
- 派生 task 可查看或可跳转

---

## 11. 遇到不确定项时的处理

若任务执行中发现以下问题：

1. 文档之间有轻微冲突
2. 当前仓库代码与冻结文档不一致
3. 一个任务需要跨多个子步骤

则采用以下策略：

### 11.1 文档冲突
优先采用当前 Phase 3 基线中的冻结结论，尤其是：
- Phase 3 主目标是 Idea，而不是 Trend
- Idea 生命周期
- Idea assess 产出结构
- Idea 到 Task 的派生链路
- Proposal / Trace / Event 治理不被绕开
- Trend / Preference 继续后置

### 11.2 代码与文档不一致
优先保护当前冻结文档中的主边界，不要盲从现有草稿代码。

### 11.3 任务过大
只落一个最小可验收子切片，并在输出中说明下一步，而不是一次把所有东西做完。

### 11.4 为最小闭环跳过能力
允许跳过，但必须：
1. 明确说明跳过了什么
2. 说明为什么现在不做
3. 写入 deferred backlog
4. 标注未来补回阶段

---

## 12. 每次输出模板

默认按下面结构向用户汇报：

### 本次完成
- 说明完成的具体小目标

### 修改文件
- 列出关键文件与职责

### 验证结果
- 列出命令 / 测试 / 构建结果

## 验证与交付补充要求

当本次改动涉及以下任一内容时，必须说明日志覆盖情况：
- 新增或修改 API
- 新增或修改状态迁移
- 新增外部调用
- 新增调度逻辑
- 新增 proposal / idea / task 核心流程

输出结果中必须额外写明：
1. 本次新增了哪些关键日志点
2. 每个日志点对应的触发时机
3. 关键日志字段是否带 `trace_id` 与业务主键
4. 失败场景下是否能通过日志快速定位问题
5. 哪些日志仍未补齐，后续在哪一步补

如果代码改动已经进入核心链路，但没有补日志说明与最小验证，不得标记为“已完成最小闭环”。

### 风险与下一步
- 写清尚未覆盖的边界
- 标出 deferred items
- 给出最合理的下一个子步骤
