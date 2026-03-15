# NoteOps-Agent · Codex Phase 1 日常使用手册

## 1. 这套 md 文档到底是干什么的

这套文档的作用是：给 Codex 提供长期上下文和执行约束。

它们本身不会自动开始开发，也不会自动把 Phase 1 全部做完。

你仍然需要给 Codex 发起一个任务。区别在于：

- 以前：每次都要手写很长的 prompt，重复讲项目背景、规则、边界、验收
- 现在：这些内容已经沉淀在仓库里的 md 文档里，你只需要发一个短 prompt，让 Codex 先读取这些文件再执行

所以，这套文档的真实价值是：

1. 减少重复写 prompt
2. 降低 Codex 理解偏差
3. 让 Phase 1 按里程碑推进
4. 让代码、表结构、文档更一致

---

## 2. 仓库内文档的职责划分

### `AGENTS.md`

仓库级长期规则。

适合放：

- 项目目标
- 仓库目录规则
- 代码改动边界
- 验证要求
- Definition of Done
- 禁止行为

这份文件通常不会频繁改。

### `docs/codex/Prompt.md`

当前阶段目标说明。

适合放：

- 当前是 Phase 1 还是 Phase 2/3
- 当前阶段的范围
- 非目标
- 冻结边界
- 核心业务约束

进入新 phase 时，这份文件要更新。

### `docs/codex/Plan.md`

当前阶段的开发计划。

适合放：

- 当前阶段的里程碑
- 每个 milestone 的目标
- 验收标准
- 推荐验证命令
- 下一步执行顺序

这份文件是 Codex “一步步推进”的主要依据。

### `docs/codex/Implement.md`

Codex 的执行方式说明。

适合放：

- 一次只做一个 milestone
- 先看代码再动手
- 先小步改动再验证
- 不要跨 milestone 发散
- 涉及表结构/接口时必须检查一致性

这份文件一般只在执行模式变化时修改。

### `docs/codex/Documentation.md`

工程状态记录。

适合放：

- 当前完成到哪里
- 已确认的架构决策
- 已知风险
- 尚未处理的问题
- 下次继续时该从哪里开始

这份文件建议持续更新。

---

## 3. 这些 md 文档会不会自动让 Codex 执行 Phase 1

不会。

这几个 md 文档是约束层，不是触发器。

它们不会因为放进仓库里，就自动让 Codex 开始开发。Codex 仍然需要一个被发起的任务，比如：

- 你手动发一个 prompt
- 你在 Codex app 里启动一次任务
- 你配置 automation 或后台任务

所以要明确一点：

**md 文档的作用是降低 prompt 负担，不是消灭 prompt。**

你以后还是要给 Codex 发任务，但不需要再把整套项目背景反复写一遍。

---

## 4. 你现在应该怎么使用 Codex

### 原则

不要再让 Codex 一次性“做完整个 Phase 1”。

正确方式是：

- 一次只做 `Plan.md` 里的一个最小未完成里程碑
- 每轮结束后检查结果
- 再让它继续下一个 milestone

也就是说，你依然是逐步推进，但不再需要把每一步拆成非常细的手工 prompt。

---

## 5. 这些 md 怎么加入到 prompt 中

不要把整份 md 内容复制进 prompt。

正确方式是：

1. 把这些 md 文件放进 `NoteOps-Agent` 仓库
2. 在 prompt 里明确要求 Codex 先读取这些文件
3. 让它只执行当前最小未完成里程碑

也就是说，你写的是“引用式 prompt”，不是“全文复制式 prompt”。

---

## 6. 日常最常用的 5 条 prompt

下面这 5 条，足够覆盖 Phase 1 的大多数场景。

### 6.1 开始执行下一个开发里程碑

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Implement the next smallest unfinished milestone from docs/codex/Plan.md.
Follow docs/codex/Implement.md.
Do not start any later milestone.
Run the narrowest relevant validation and report:
1) changed files
2) validation results
3) remaining next step
```

适用场景：

- 开始今天的开发
- 让 Codex 继续 Phase 1 的下一个功能切片
- 不想自己再手工细拆步骤

### 6.2 继续上一轮开发

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Continue from the current repository state.
Read AGENTS.md and docs/codex/*.md again before making changes.
Complete only the next unfinished milestone in Plan.md.
Keep the change minimal and reviewable.
```

适用场景：

- 上一轮做完了一个 milestone
- 你 review 后觉得可以继续
- 想让 Codex 接着做下一步

### 6.3 做完代码后同步文档

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Inspect the recent code changes.
Update the minimum necessary docs to keep code, schema, and docs aligned.
Do not invent features or behavior that are not implemented.
```

适用场景：

- 刚做完接口、表结构、流程变更
- 需要补文档
- 避免代码和 docs 脱节

### 6.4 检查表结构 / DTO / 状态机一致性

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Inspect the schema-related changes.
Check consistency across migration, entity/model, DTO, controller/API behavior, frontend usage, and docs.
Return every mismatch explicitly.
```

适用场景：

- 改了数据库表
- 改了 DTO 或接口字段
- 改了状态机、枚举、任务状态
- 想先查有没有漏改的地方

### 6.5 只让 Codex 先做计划，不直接写代码

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Do not implement yet.
Review the current repository state and Plan.md.
Tell me:
1) the next best milestone to implement
2) affected modules
3) likely risks
4) validation to run after implementation
```

适用场景：

- 你不确定下一个 milestone 应该做什么
- 想先看 Codex 的实施判断
- 想先 review 再让它动代码

---

## 7. 如果你要用 skills，怎么发

如果仓库里已经放了 `.agents/skills/`，那么 prompt 可以更短。

### 7.1 执行下一个里程碑

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask you to do so.
When summarizing changes, explaining decisions, or reporting validation results, use Simplified Chinese.
$noteops-phase-implement
Read AGENTS.md and docs/codex/*.md.
Complete the next unfinished milestone only.
```

### 7.2 同步文档

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask you to do so.
When summarizing changes, explaining decisions, or reporting validation results, use Simplified Chinese.
$noteops-doc-sync
Inspect recent code changes and sync the minimum necessary docs.
```

### 7.3 守卫表结构与接口一致性

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask you to do so.
When summarizing changes, explaining decisions, or reporting validation results, use Simplified Chinese.
$noteops-schema-guard
Check migration, DTO, entity, API behavior, frontend usage, and docs consistency.
```

---

## 8. 你的实际工作流应该是什么样

建议按这个顺序使用：

### 第一步：让 Codex 做当前最小里程碑

用“开始执行下一个开发里程碑”那条 prompt。

### 第二步：你 review 改动结果

重点看：

- 有没有超范围
- 有没有乱改目录
- 有没有擅自扩大功能
- 有没有漏掉验证

### 第三步：如涉及表结构、状态机、字段变更

用“一致性检查”那条 prompt 再检查一遍。

### 第四步：需要时同步文档

用“同步文档”那条 prompt。

### 第五步：进入下一里程碑

用“继续上一轮开发”那条 prompt。

---

## 9. 哪些情况不要让 Codex 一次做太多

下面这些情况，不建议一口气做完整功能链：

- 同时涉及数据库、后端接口、前端页面、状态机
- 需求还没彻底冻结
- 当前仓库结构还在快速变化
- 你自己都没法清楚描述 done 条件
- PRD 里还有明显待确认点

这种场景下，应该坚持“一次一个 milestone”。

---

## 10. 什么时候需要修改这些 md 文档

### 一般不用频繁改

- `AGENTS.md`
- `Implement.md`
- skills

### 进入新 phase 时通常要改

- `Prompt.md`
- `Plan.md`
- `Documentation.md`

判断方式：

- 如果变的是“当前目标和阶段计划”，改 `Prompt.md / Plan.md / Documentation.md`
- 如果变的是“仓库长期规则和工作流”，改 `AGENTS.md / skills / Implement.md`

---

## 11. 对 NoteOps-Agent 的一个重要使用建议

对你当前阶段，最适合的用法不是：

“让 Codex 一次做完整个 Phase 1。”

而是：

“让 Codex 一次完成 `Plan.md` 里的一个最小未完成里程碑，并强制回报验证结果和下一步。”

这样你才能真正控制项目，而不是让它发散生长。

---

## 12. 最短可直接复制使用的版本

如果今天只想马上开始，直接发这一条就够了：

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Implement the next smallest unfinished milestone from Plan.md only.
Run narrow validation and report changed files, validation results, and the remaining next step.
```

如果今天只想继续上一轮，就发这一条：

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Continue from the current repository state.
Read AGENTS.md and docs/codex/*.md again.
Complete only the next unfinished milestone in Plan.md.
```

如果今天只想补文档，就发这一条：

```text
Always communicate with me in Simplified Chinese, even if my prompt is in English.
Do not translate code, identifiers, file paths, shell commands, SQL, API paths, database fields, or error/log messages unless I explicitly ask.

Read AGENTS.md and docs/codex/*.md.
Inspect recent code changes and update only the necessary docs to keep them aligned.
```

---

## 13. 一句话总结

这套 md 文档不会自动替你开发，但它能把“每次都重写一大段 prompt”的低效方式，变成“仓库里长期沉淀规则 + 每次只发很短启动指令”的可持续方式。