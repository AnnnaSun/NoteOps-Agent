# Review AI Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Review AI 从 `today` 和 `complete` 的同步主链路中剥离，让首页和提交结果先返回基础业务数据，再通过独立 `prep` / `feedback` read 接口按需加载 AI 内容。

**Architecture:** 保留现有 Review 主业务接口与状态机，把 `review-render` 和 `review-feedback` 从同步编排改为独立只读能力。`GET /reviews/today` 和 `GET /workspace/today` 只返回基础持久字段；`POST /reviews/{id}/complete` 只完成主业务；新增 `GET /reviews/{id}/prep` 和 `GET /reviews/{id}/feedback` 供前端懒加载与自动补齐。

**Tech Stack:** Spring Boot 3.5 + JDBC + React 18 + TypeScript + Vite + MockMvc/JUnit

---

### Task 1: 重构后端 Review 服务边界

**Files:**
- Modify: `server/src/main/java/com/noteops/agent/service/review/ReviewApplicationService.java`
- Modify: `server/src/main/java/com/noteops/agent/service/workspace/WorkspaceApplicationService.java`
- Test: `server/src/test/java/com/noteops/agent/service/review/ReviewApplicationServiceTest.java`

- [ ] **Step 1: 写出 `listToday` 不再同步跑 AI 的失败测试**

在 `ReviewApplicationServiceTest` 新增测试，断言：

```java
@Test
void listTodayReturnsBaseItemsWithoutInvokingAiRender() {
    UUID userId = UUID.randomUUID();
    UUID noteId = UUID.randomUUID();
    InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
    noteRepository.notes.add(noteSummary(userId, noteId, "Base note"));
    InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
    reviewStateRepository.create(userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
        null, null, BigDecimal.ZERO, null, NOW, 0, 0);

    RecordingReviewAiAssistant assistant = new RecordingReviewAiAssistant();
    ReviewApplicationService service = newService(
        reviewStateRepository,
        noteRepository,
        new InMemoryTaskRepository(),
        new RecordingAgentTraceRepository(),
        new RecordingUserActionEventRepository(),
        new RecordingToolInvocationLogRepository(),
        assistant
    );

    List<ReviewApplicationService.ReviewTodayItemView> items = service.listToday(userId.toString());

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().aiRecallSummary()).isNull();
    assertThat(items.getFirst().aiReviewKeyPoints()).isEmpty();
    assertThat(items.getFirst().aiExtensionPreview()).isNull();
    assertThat(assistant.renderInvocations).isEqualTo(0);
}
```

- [ ] **Step 2: 实现 `listToday` 只返回基础数据**

在 `ReviewApplicationService.listToday(...)` 中把：

```java
return enrichTodayItemsWithAi(userId, items);
```

改成：

```java
return items;
```

保留 `toTodayItem(...)` 中现有基础字段填充，确保：
- `title`
- `currentSummary`
- `currentKeyPoints`
- `currentTags`
- queue meta

仍然正常返回。

- [ ] **Step 3: 写出 `complete` 不再同步跑 AI feedback 的失败测试**

在 `ReviewApplicationServiceTest` 新增测试，断言：

```java
@Test
void completeReturnsBusinessResultWithoutInvokingAiFeedback() {
    UUID userId = UUID.randomUUID();
    UUID noteId = UUID.randomUUID();
    InMemoryReviewStateRepository reviewStateRepository = new InMemoryReviewStateRepository();
    ReviewApplicationService.ReviewStateView schedule = reviewStateRepository.create(
        userId, noteId, ReviewQueueType.SCHEDULE, ReviewCompletionStatus.NOT_STARTED, null,
        null, null, BigDecimal.valueOf(40), null, NOW, 0, 0
    );

    CompletionFeedbackReviewAiAssistant assistant = new CompletionFeedbackReviewAiAssistant();
    ReviewApplicationService service = newService(
        reviewStateRepository,
        new InMemoryNoteRepository(),
        new InMemoryTaskRepository(),
        new RecordingAgentTraceRepository(),
        new RecordingUserActionEventRepository(),
        new RecordingToolInvocationLogRepository(),
        assistant
    );

    ReviewApplicationService.ReviewCompletionView result = service.complete(
        schedule.id().toString(),
        new ReviewApplicationService.CompleteReviewCommand(userId.toString(), "PARTIAL", "TIME_LIMIT", null, null)
    );

    assertThat(result.completionStatus()).isEqualTo(ReviewCompletionStatus.PARTIAL);
    assertThat(result.recallFeedbackSummary()).isNull();
    assertThat(result.extensionSuggestions()).isEmpty();
    assertThat(assistant.feedbackInvocations).isEqualTo(0);
}
```

- [ ] **Step 4: 实现 `complete` 只返回主业务结果**

在 `ReviewApplicationService.complete(...)` 中删除同步 AI feedback 编排，保留：
- review 状态推进
- task 规则同步
- `review.complete` tool log
- 主业务 trace / event / structured log

把：

```java
CompletionFeedback feedback = buildCompletionFeedback(...);
```

替换为：

```java
CompletionFeedback feedback = CompletionFeedback.notLoaded();
```

并新增：

```java
private static CompletionFeedback notLoaded() {
    return new CompletionFeedback("NOT_REQUESTED", null, null, List.of(), null);
}
```

同时移除 `complete(...)` 中和 `review.ai-feedback` 直接绑定的 event/log 调用。

- [ ] **Step 5: 运行服务层测试**

运行：

```bash
mvn -q -Dtest=ReviewApplicationServiceTest test -f server/pom.xml
```

预期：

```text
Process exited with code 0
```

### Task 2: 新增 `prep` / `feedback` read 接口

**Files:**
- Modify: `server/src/main/java/com/noteops/agent/controller/review/ReviewController.java`
- Modify: `server/src/main/java/com/noteops/agent/service/review/ReviewApplicationService.java`
- Create: `server/src/main/java/com/noteops/agent/dto/review/ReviewPrepResponse.java`
- Create: `server/src/main/java/com/noteops/agent/dto/review/ReviewFeedbackResponse.java`
- Test: `server/src/test/java/com/noteops/agent/controller/review/ReviewControllerTest.java`
- Test: `server/src/test/java/com/noteops/agent/service/review/ReviewApplicationServiceTest.java`

- [ ] **Step 1: 写出 controller 层新接口的失败测试**

在 `ReviewControllerTest` 新增两个测试：

```java
@Test
void returnsReviewPrepWithEnvelope() throws Exception { ... }

@Test
void returnsReviewFeedbackWithEnvelope() throws Exception { ... }
```

断言：
- `GET /api/v1/reviews/{id}/prep?user_id=...` 返回 AI prep 字段
- `GET /api/v1/reviews/{id}/feedback?user_id=...` 返回 AI feedback 字段

- [ ] **Step 2: 定义最小 response DTO**

创建 `ReviewPrepResponse.java`：

```java
public record ReviewPrepResponse(
    @JsonProperty("review_item_id") String reviewItemId,
    @JsonProperty("ai_recall_summary") String aiRecallSummary,
    @JsonProperty("ai_review_key_points") List<String> aiReviewKeyPoints,
    @JsonProperty("ai_extension_preview") String aiExtensionPreview
) { }
```

创建 `ReviewFeedbackResponse.java`：

```java
public record ReviewFeedbackResponse(
    @JsonProperty("review_item_id") String reviewItemId,
    @JsonProperty("recall_feedback_summary") String recallFeedbackSummary,
    @JsonProperty("next_review_hint") String nextReviewHint,
    @JsonProperty("extension_suggestions") List<String> extensionSuggestions,
    @JsonProperty("follow_up_task_suggestion") String followUpTaskSuggestion
) { }
```

- [ ] **Step 3: 在服务层新增 `getPrep`**

在 `ReviewApplicationService` 中新增：

```java
public ReviewPrepView getPrep(String reviewItemIdRaw, String userIdRaw) { ... }
```

实现要点：
- 校验 `reviewItemId` 和 `user_id`
- 读取对应 review state
- 找到 note summary
- 调 `reviewAiAssistant.renderTodayItems(...)`，只传单条 candidate
- 写 `REVIEW_AI_RENDER` trace / `review.ai-render` tool log / 对应 user event
- 成功时返回 AI 字段
- 失败时抛 `ApiException`，由 controller 返回 error envelope

- [ ] **Step 4: 在服务层新增 `getFeedback`**

在 `ReviewApplicationService` 中新增：

```java
public ReviewFeedbackView getFeedback(String reviewItemIdRaw, String userIdRaw) { ... }
```

实现要点：
- 校验 `reviewItemId` 和 `user_id`
- 读取当前 review state + 对应 note summary
- 调 `reviewAiAssistant.buildCompletionFeedback(...)`
- 沿用现有 fallback 规则：
  - `normalizeCompletionFeedback(...)`
- 写 `review.ai-feedback` tool log、对应 trace / event / structured log
- 成功返回 feedback view

- [ ] **Step 5: 在 controller 中暴露两个新 GET 接口**

在 `ReviewController.java` 中新增：

```java
@GetMapping("/{reviewItemId}/prep")
public ApiEnvelope<ReviewPrepResponse> prep(@PathVariable String reviewItemId,
                                            @RequestParam("user_id") String userId) { ... }

@GetMapping("/{reviewItemId}/feedback")
public ApiEnvelope<ReviewFeedbackResponse> feedback(@PathVariable String reviewItemId,
                                                    @RequestParam("user_id") String userId) { ... }
```

- [ ] **Step 6: 运行 controller + service 测试**

运行：

```bash
mvn -q -Dtest=ReviewControllerTest,ReviewApplicationServiceTest test -f server/pom.xml
```

预期：

```text
Process exited with code 0
```

### Task 3: 调整前端为懒加载 prep 和自动加载 feedback

**Files:**
- Modify: `web/src/api.ts`
- Modify: `web/src/types.ts`
- Modify: `web/src/App.tsx`

- [ ] **Step 1: 先补前端类型与 API 方法**

在 `web/src/types.ts` 新增：

```ts
export type ReviewPrepResult = {
  review_item_id: string;
  ai_recall_summary: string | null;
  ai_review_key_points: string[];
  ai_extension_preview: string | null;
};

export type ReviewFeedbackResult = {
  review_item_id: string;
  recall_feedback_summary: string | null;
  next_review_hint: string | null;
  extension_suggestions: string[];
  follow_up_task_suggestion: string | null;
};
```

在 `web/src/api.ts` 新增：

```ts
export function getReviewPrep(reviewItemId: string, userId: string): Promise<ReviewPrepResult> { ... }
export function getReviewFeedback(reviewItemId: string, userId: string): Promise<ReviewFeedbackResult> { ... }
```

- [ ] **Step 2: 把 Review 卡片首屏回退到基础字段**

在 `web/src/App.tsx` 中把 Today Review 卡片的默认显示逻辑调整为：

```tsx
const recallSummary = prepState?.ai_recall_summary ?? review.current_summary;
const recallKeyPoints =
  prepState?.ai_review_key_points?.length ? prepState.ai_review_key_points : review.current_key_points;
const extensionPreview = prepState?.ai_extension_preview ?? null;
```

要求：
- 首屏不依赖 `review.ai_*`
- 仍保持三段式准备卡片结构

- [ ] **Step 3: 为 prep 懒加载增加局部状态**

在 `App.tsx` 中新增：

```ts
const [reviewPrepById, setReviewPrepById] = useState<Record<string, ReviewPrepResult>>({});
const [loadingReviewPrepIds, setLoadingReviewPrepIds] = useState<Record<string, boolean>>({});
```

并在 `handleReviewFormToggle(review)` 或独立 helper 中加入：

```ts
if (!reviewPrepById[review.id] && !loadingReviewPrepIds[review.id]) {
  void loadReviewPrep(review.id);
}
```

`loadReviewPrep` 逻辑：
- 只请求一次
- 成功后写入 `reviewPrepById`
- 失败静默降级，不抛全局错误

- [ ] **Step 4: 把 complete 后 AI feedback 改成独立请求**

把当前：

```ts
const result = await completeReview(...);
setLastReviewFeedback({ reviewId: review.id, result });
```

调整为：

```ts
const result = await completeReview(...);
setLastReviewFeedback({
  reviewId: review.id,
  result: {
    ...result,
    recall_feedback_summary: null,
    next_review_hint: null,
    extension_suggestions: [],
    follow_up_task_suggestion: null
  },
  isLoading: true
});
void loadReviewFeedback(review.id);
```

并新增 `loadReviewFeedback(reviewId)`：
- 调 `getReviewFeedback(reviewId, activeUserId)`
- 成功后合并更新 `lastReviewFeedback`
- 失败时把 `isLoading` 置为 false，保留主业务结果

- [ ] **Step 5: 给 feedback banner 增加轻量 loading 态**

在 `App.tsx` 中，当 `lastReviewFeedback.isLoading` 为 `true` 时展示：

```tsx
<p className="search-result-detail">正在补充 Review AI 反馈...</p>
```

不要阻断其他 UI。

- [ ] **Step 6: 运行前端构建**

运行：

```bash
npm run build
```

预期：

```text
vite v5.x building for production...
✓ built in ...
```

### Task 4: 同步文档并做全量验证

**Files:**
- Modify: `docs/reality/Feature-Status-Matrix.md`
- Modify: `docs/reality/Implementation-Inventory.md`
- Modify: `docs/codex/Documentation.md`

- [ ] **Step 1: 更新 `docs/reality/Feature-Status-Matrix.md`**

把这些描述改成现实结果：

- `Workspace Today`
  - 从“Review 区使用三段式准备卡片显示 AI 字段与 fallback 内容”
  - 改成“首屏显示基础数据；展开 review 后懒加载 prep AI”
- `Review AI 反馈展示`
  - 改成“complete 成功后独立加载 feedback”

- [ ] **Step 2: 更新 `docs/reality/Implementation-Inventory.md`**

补充：
- `GET /api/v1/reviews/{id}/prep`
- `GET /api/v1/reviews/{id}/feedback`
- `workspace/today` 不再同步触发 review AI
- `complete` 不再同步等待 feedback AI

- [ ] **Step 3: 更新 `docs/codex/Documentation.md`**

把 Review AI 最小增强章节改为真实实现：
- today 首屏只返回基础数据
- prep 改为展开时懒加载
- feedback 改为 complete 后独立读取

- [ ] **Step 4: 运行全量验证**

运行：

```bash
npm run build
mvn -q test -f server/pom.xml
```

预期：

```text
✓ built in ...
Process exited with code 0
```

- [ ] **Step 5: 最终人工核对**

核对：
- 首页进入时 review/task 不再长时间为 0
- 展开某条 review 才触发 prep AI
- complete 先返回主业务，再异步补 feedback
- AI 失败不影响主链
- 文档没有把缓存/job/重试写成已实现
