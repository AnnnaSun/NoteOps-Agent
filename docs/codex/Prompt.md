# Prompt
# docs/codex/Prompt.md

## 1. 当前目标

当前仓库进入 **Phase 1 重启执行基线**。Codex 的唯一目标是：  
在不偏离冻结文档的前提下，构建一个可运行、可验证、可继续迭代的 **NoteOps Phase 1 最小知识内核**。

这个阶段不是为了“把所有未来能力做完”，而是为了落下后续 Phase 2~5 的稳定骨架。

---

## 2. 当前产品语义（必须保持）

### 2.1 系统定位

NoteOps 是一个以 Note 为第一公民的多 Agent Knowledge-to-Action 系统。  
系统重点不在“存笔记”，而在：

1. 接收输入
2. 结构化处理
3. 归并为知识
4. 派生复习/任务
5. 保留建议、确认、应用、撤销、追溯链路

### 2.2 当前最重要的五个约束

1. **PostgreSQL 是唯一真相源**
2. **原始内容只追加，不静默覆盖**
3. **当前解释层通过 proposal 演化**
4. **Review 采用双池语义**
5. **Task 同时承接 SYSTEM / USER**

---

## 3. 本轮开发范围（Phase 1）

### 3.1 必须完成的主链路

#### A. Capture 主链路
- 接收 TEXT / URL 输入
- 创建 CaptureJob
- 状态推进：RECEIVED → EXTRACTING → ANALYZING → CONSOLIDATING → COMPLETED/FAILED
- 产出 Note 创建或归并结果

#### B. Note 主链路
- `notes` 存当前视图
- `note_contents` 存原始内容与追加块
- Note 详情可返回当前摘要、关键点、来源链与关联计数

#### C. Review 主链路
- 基础 Today 队列
- Review completion 支持 completion_status / completion_reason
- ReviewState 至少支持 `SCHEDULE` / `RECALL`
- 完成后可更新下次时间、进入 recall 或触发 follow-up task

#### D. Task 主链路
- 同时支持 System Task 与 User Task
- 支持创建、查看 Today、完成、跳过
- 能绑定 NOTE / IDEA / REVIEW / NONE

#### E. Proposal / Trace 主链路
- 生成 change proposal
- 支持 apply / rollback
- 写入 trace / tool log / user action event

---

## 4. 当前不做的内容

以下能力即使存在文档，也只允许预留，不允许抢占主线实现时间：

1. 完整 Idea 生命周期
2. Trend Inbox 全链路
3. Preference Learning 完整画像计算
4. 外部检索增强的完整产品化界面
5. PWA 离线完整实现
6. Android / iOS
7. 导出中心
8. 复杂权限 / 多租户

---

## 5. 建模与接口冻结点

### 5.1 核心表（Phase 1）

必须优先落表：

- `notes`
- `note_contents`
- `review_states`
- `tasks`
- `change_proposals`
- `capture_jobs`
- `agent_traces`
- `tool_invocation_logs`
- `user_action_events`

### 5.2 核心接口（Phase 1）

优先实现：

- `POST /api/v1/captures`
- `GET /api/v1/captures/{id}`
- `GET /api/v1/notes`
- `GET /api/v1/notes/{id}`
- `GET /api/v1/reviews/today`
- `POST /api/v1/reviews/{review_item_id}/complete`
- `POST /api/v1/tasks`
- `GET /api/v1/tasks/today`
- `POST /api/v1/tasks/{task_id}/complete`
- `POST /api/v1/tasks/{task_id}/skip`
- `POST /api/v1/notes/{note_id}/change-proposals/{proposal_id}/apply`
- `POST /api/v1/change-proposals/{id}/rollback`

---

## 6. 当前交付标准

当前阶段的代码必须满足：

1. 结构清晰，可继续扩展
2. 不透支未来阶段，但保留清晰扩展点
3. 至少能支撑本地跑通数据库迁移、核心接口、最小页面或最小 API 骨架
4. 能用 trace / event / proposal 解释系统做了什么
5. 文档与代码一致

---

## 7. Codex 执行偏好

执行时优先级如下：

1. 正确性
2. 最小闭环
3. 可验证性
4. 可维护性
5. 可扩展性

低优先级：
- 炫技式抽象
- 提前泛化
- 大规模“为了未来”的模板化设计

---

## 8. 每次任务的默认思路

Codex 在接到具体任务后，应默认按以下顺序工作：

1. 先读取 `AGENTS.md`
2. 再读取本目录下 `Plan.md`
3. 识别当前任务属于哪一个 Phase 1 里程碑
4. 只实现该里程碑要求的最小闭环
5. 跑最小必要验证
6. 更新 `Documentation.md` 中的进度或风险

---

## 9. 当前阶段一句话目标

**先把 NoteOps Phase 1 做成“真能跑、真能解释、真能继续开发”的知识内核，而不是看起来很全但没有主骨架的演示项目。**
