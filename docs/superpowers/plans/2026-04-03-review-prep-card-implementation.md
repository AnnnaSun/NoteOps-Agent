# Review Preparation Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Today Workspace 中的 Review 卡片改为三段式“复习前准备区”，突出 `AI 回忆摘要`，弱化 `回忆支点` 和 `必要延伸`，同时同步 reality 文档并完成最小验证。

**Architecture:** 保持现有 `web/src/App.tsx` 单页工作台结构，不新增后端字段，不拆新组件文件，只在 Review Today 卡片渲染处增加一个小的展示辅助层和对应样式。文档同步仅更新 `docs/reality` 中与前端现实展示相关的条目，不修改产品基线文档。

**Tech Stack:** React 18 + TypeScript + Vite + 现有 `web/src/styles.css`

---

### Task 1: 重排 Review Today 卡片结构

**Files:**
- Modify: `web/src/App.tsx`

- [ ] **Step 1: 在 Review Today 渲染前提取三段式展示数据**

在 `reviewsToday.map((review) => ...)` 回调里，先定义局部展示变量：

```tsx
const recallSummary = review.ai_recall_summary ?? review.current_summary;
const recallKeyPoints =
  review.ai_review_key_points.length > 0 ? review.ai_review_key_points : review.current_key_points;
const extensionPreview = review.ai_extension_preview?.trim() || null;
```

目的：
- 消除 JSX 内部重复三元表达式
- 保持 fallback 语义集中且可读

- [ ] **Step 2: 将正文改成三段式纵向 section**

把当前这段结构：

```tsx
<strong>{review.title}</strong>
<p>{review.ai_recall_summary ?? review.current_summary}</p>
...
```

改成：

```tsx
<strong>{review.title}</strong>
<div className="review-prep-card">
  <section className="review-prep-section review-prep-section-primary">
    <p className="review-prep-label">AI 回忆摘要</p>
    <p className="review-prep-summary">{recallSummary}</p>
  </section>

  {recallKeyPoints.length > 0 ? (
    <section className="review-prep-section review-prep-section-secondary">
      <p className="review-prep-label">回忆支点</p>
      <div className="note-card-points review-prep-points">
        {recallKeyPoints.slice(0, 4).map((point) => (
          <span key={`${review.id}-${point}`} className="note-card-point">
            {point}
          </span>
        ))}
      </div>
    </section>
  ) : null}

  {extensionPreview ? (
    <section className="review-prep-section review-prep-section-tertiary">
      <p className="review-prep-label">必要延伸</p>
      <p className="review-prep-extension">{extensionPreview}</p>
    </section>
  ) : null}
</div>
```

约束：
- 保留卡片标题、meta、按钮和表单位置不变
- 不改 `lastReviewFeedback` banner
- 不增加新交互

- [ ] **Step 3: 运行前端构建前先做一次静态检查**

人工检查：
- `review-prep-*` class 名只在 Review 卡片区域使用
- 没有改动 Review complete banner 数据流
- 没有引入新的类型依赖

### Task 2: 增加三段式准备区样式

**Files:**
- Modify: `web/src/styles.css`

- [ ] **Step 1: 增加 Review Preparation Card 样式组**

在 `web/src/styles.css` 的 Review / list-row 相关区域新增样式：

```css
.review-prep-card {
  display: grid;
  gap: 10px;
}

.review-prep-section {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.review-prep-section-primary {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(244, 250, 255, 0.96));
}

.review-prep-section-secondary {
  background: rgba(248, 250, 252, 0.82);
}

.review-prep-section-tertiary {
  background: rgba(255, 255, 255, 0.74);
}

.review-prep-label {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-500);
}

.review-prep-summary {
  margin: 0;
  color: var(--ink-900);
  font-size: 0.98rem;
  line-height: 1.55;
}

.review-prep-points {
  margin-top: 0;
}

.review-prep-extension {
  margin: 0;
  color: var(--ink-600);
  font-size: 0.9rem;
}
```

目标：
- 主区更亮、更清晰
- 次区与第三段保持更轻层级
- 继续沿用现有变量，不引入新主题系统

- [ ] **Step 2: 补移动端和紧凑场景检查**

确认这些规则不会破坏已有响应式：
- 卡片不出现水平滚动
- section 标题和 points 在窄屏下仍能换行
- meta / button / form 位置不被 section 挤坏

如果需要，只做最小样式补丁，例如：

```css
@media (max-width: 720px) {
  .review-prep-section {
    padding: 10px;
  }

  .review-prep-summary {
    font-size: 0.94rem;
  }
}
```

### Task 3: 同步 reality 文档并验证

**Files:**
- Modify: `docs/reality/Feature-Status-Matrix.md`
- Modify: `docs/reality/Implementation-Inventory.md`

- [ ] **Step 1: 更新 Feature Status Matrix**

把与 Review Today UI 相关的描述收紧到真实状态：

- `Workspace Today`
  - 从“Review 区含 AI 展示字段”更新为“Review 区含三段式准备卡片与 AI 展示字段”
- `Review Today 列表`
  - 从“优先显示 AI recall 字段”更新为“三段式显示 AI 回忆摘要 / 回忆支点 / 必要延伸，缺失时 fallback”

- [ ] **Step 2: 更新 Implementation Inventory**

把前端现实描述收紧为：

- `Workspace Today`
  - Review 区域现在使用三段式准备结构
- `Review AI 反馈 banner`
  - 仍保留，不属于本切片改动

避免写成：
- 已实现 AI 降级状态可见性
- 已实现开始回忆引导

- [ ] **Step 3: 运行验证命令**

运行：

```bash
npm run build
```

预期：

```text
vite v5.x building for production...
✓ built in ...
```

- [ ] **Step 4: 复跑后端回归以防前端提交夹带影响**

运行：

```bash
mvn -q test -f server/pom.xml
```

预期：

```text
Process exited with code 0
```

- [ ] **Step 5: 最终人工核对**

核对清单：
- Review Today 卡片形成明确三段结构
- `AI 回忆摘要` 是默认视觉焦点
- `回忆支点` 和 `必要延伸` 视觉弱于主区
- AI 字段缺失时不崩溃，仍能 fallback
- `lastReviewFeedback` banner 仍可显示
