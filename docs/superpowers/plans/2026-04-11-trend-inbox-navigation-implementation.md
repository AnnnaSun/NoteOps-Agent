# Trend Inbox Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight `Home` entry page plus independent `Notes` / `Ideas` / `Workspace` / `Trend Inbox` views, wire `Capture` into `Home`, add `GET /api/v1/trends/inbox`, and complete the real `IGNORE` action without crossing into Step 4.6 conversion work.

**Architecture:** Keep the server split between controller, application/query service, and repository layers. On the web side, replace the current single heavy mixed workspace with a small top-level view state and focused page components; keep selection logic in pure helper modules so it can be tested with `node:test` without introducing a new UI test stack.

**Tech Stack:** Spring Boot, JdbcClient, Java 21, React 18, TypeScript, Vite, Node `node:test`

---

## File Map

### Server

- Create: `server/src/main/java/com/noteops/agent/controller/trend/TrendInboxController.java`
- Create: `server/src/main/java/com/noteops/agent/controller/trend/TrendActionController.java`
- Create: `server/src/main/java/com/noteops/agent/service/trend/TrendInboxQueryService.java`
- Create: `server/src/main/java/com/noteops/agent/service/trend/TrendActionApplicationService.java`
- Create: `server/src/test/java/com/noteops/agent/controller/trend/TrendInboxControllerTest.java`
- Create: `server/src/test/java/com/noteops/agent/controller/trend/TrendActionControllerTest.java`
- Create: `server/src/test/java/com/noteops/agent/service/trend/TrendActionApplicationServiceTest.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/trend/TrendActionRequest.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/TrendItemRepository.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/JdbcTrendItemRepository.java`
- Modify: `server/src/test/java/com/noteops/agent/repository/trend/JdbcTrendItemRepositoryIntegrationTest.java`

### Web

- Create: `web/src/navigationState.ts`
- Create: `web/src/homeState.ts`
- Create: `web/src/trendInboxState.ts`
- Create: `web/src/views/HomeView.tsx`
- Create: `web/src/views/NotesView.tsx`
- Create: `web/src/views/IdeasView.tsx`
- Create: `web/src/views/WorkspaceView.tsx`
- Create: `web/src/views/TrendInboxView.tsx`
- Create: `web/tests/navigationState.test.ts`
- Create: `web/tests/homeState.test.ts`
- Create: `web/tests/trendInboxState.test.ts`
- Modify: `web/src/App.tsx`
- Modify: `web/src/api.ts`
- Modify: `web/src/types.ts`
- Modify: `web/src/styles.css`

### Docs

- Modify: `docs/codex/Documentation.md`

---

### Task 1: Add `GET /api/v1/trends/inbox` backend query path

**Files:**
- Create: `server/src/main/java/com/noteops/agent/controller/trend/TrendInboxController.java`
- Create: `server/src/main/java/com/noteops/agent/service/trend/TrendInboxQueryService.java`
- Create: `server/src/test/java/com/noteops/agent/controller/trend/TrendInboxControllerTest.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/TrendItemRepository.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/JdbcTrendItemRepository.java`
- Modify: `server/src/test/java/com/noteops/agent/repository/trend/JdbcTrendItemRepositoryIntegrationTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
@WebMvcTest(TrendInboxController.class)
@Import(ApiExceptionHandler.class)
class TrendInboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendInboxQueryService trendInboxQueryService;

    @Test
    void listsAnalyzedTrendInboxItems() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID trendItemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(trendInboxQueryService.list(any()))
            .thenReturn(List.of(
                new TrendItemRepository.TrendItemRecord(
                    trendItemId,
                    userId,
                    TrendSourceType.GITHUB,
                    "repo#123",
                    "OpenAI adds workflow primitives",
                    "https://example.com",
                    "Structured summary",
                    0.92,
                    TrendAnalysisPayload.fromMap(Map.of(
                        "summary", "Structured summary",
                        "why_it_matters", "Affects agent orchestration",
                        "topic_tags", List.of("agent", "tooling"),
                        "signal_type", "PRODUCT",
                        "note_worthy", true,
                        "idea_worthy", false,
                        "suggested_action", "SAVE_AS_NOTE",
                        "reasoning_summary", "Useful for note capture"
                    )),
                    Map.of(),
                    TrendItemStatus.ANALYZED,
                    TrendActionType.SAVE_AS_NOTE,
                    Instant.parse("2026-04-11T10:15:30Z"),
                    Instant.parse("2026-04-11T10:20:30Z"),
                    null,
                    null,
                    Instant.parse("2026-04-11T10:20:30Z"),
                    Instant.parse("2026-04-11T10:21:30Z")
                )
            ));

        mockMvc.perform(get("/api/v1/trends/inbox")
                .param("user_id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data[0].source_type").value("GITHUB"))
            .andExpect(jsonPath("$.data[0].suggested_action").value("SAVE_AS_NOTE"));
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendInboxControllerTest
```

Expected: FAIL because `TrendInboxController` and `TrendInboxQueryService` do not exist yet.

- [ ] **Step 3: Write the failing repository integration test for default analyzed-only sorting**

```java
@Test
void listsInboxItemsByUserWithDefaultUpdatedAtDescOrdering() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    TrendItemRecord olderAnalyzed = repository.create(
        userId,
        TrendSourceType.HN,
        "hn-1",
        "Older analyzed item",
        "https://news.ycombinator.com/item?id=1",
        "Older summary",
        0.70,
        analyzedPayload("Older summary", "HN signal"),
        Map.of(),
        TrendItemStatus.ANALYZED,
        TrendActionType.SAVE_AS_NOTE,
        Instant.parse("2026-04-10T10:00:00Z"),
        Instant.parse("2026-04-10T10:00:00Z"),
        null,
        null
    );
    TrendItemRecord newerAnalyzed = repository.create(
        userId,
        TrendSourceType.GITHUB,
        "gh-1",
        "Newer analyzed item",
        "https://github.com/openai/openai",
        "Newer summary",
        0.91,
        analyzedPayload("Newer summary", "GitHub signal"),
        Map.of(),
        TrendItemStatus.ANALYZED,
        TrendActionType.PROMOTE_TO_IDEA,
        Instant.parse("2026-04-11T10:00:00Z"),
        Instant.parse("2026-04-11T10:00:00Z"),
        null,
        null
    );
    repository.create(
        userId,
        TrendSourceType.HN,
        "hn-ignored",
        "Ignored item",
        "https://news.ycombinator.com/item?id=2",
        "Ignored summary",
        0.40,
        TrendAnalysisPayload.empty(),
        Map.of(),
        TrendItemStatus.IGNORED,
        TrendActionType.IGNORE,
        Instant.parse("2026-04-11T09:00:00Z"),
        Instant.parse("2026-04-11T09:00:00Z"),
        null,
        null
    );

    List<TrendItemRecord> inboxItems = repository.listInbox(userId, TrendItemStatus.ANALYZED, null);

    assertThat(inboxItems).extracting(TrendItemRecord::id)
        .containsExactly(newerAnalyzed.id(), olderAnalyzed.id());
}
```

- [ ] **Step 4: Run the repository test to verify it fails**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=JdbcTrendItemRepositoryIntegrationTest
```

Expected: FAIL because `listInbox(...)` is not implemented.

- [ ] **Step 5: Add repository support for filtered inbox listing**

```java
List<TrendItemRecord> listInbox(UUID userId,
                                TrendItemStatus status,
                                TrendSourceType sourceType);
```

```java
@Override
public List<TrendItemRecord> listInbox(UUID userId,
                                       TrendItemStatus status,
                                       TrendSourceType sourceType) {
    StringBuilder sql = new StringBuilder(BASE_SELECT)
        .append(" where user_id = :userId");
    if (status != null) {
        sql.append(" and status = :status");
    }
    if (sourceType != null) {
        sql.append(" and source_type = :sourceType");
    }
    sql.append(" order by updated_at desc");

    JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
        .param("userId", userId);
    if (status != null) {
        spec.param("status", status.name());
    }
    if (sourceType != null) {
        spec.param("sourceType", sourceType.name());
    }
    return spec.query((rs, rowNum) -> mapRow(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("source_type"),
        rs.getString("source_item_key"),
        rs.getString("title"),
        rs.getString("url"),
        rs.getString("summary"),
        rs.getDouble("normalized_score"),
        rs.getString("analysis_payload"),
        rs.getString("extra_attributes"),
        rs.getString("status"),
        rs.getString("suggested_action"),
        rs.getTimestamp("source_published_at"),
        rs.getTimestamp("last_ingested_at"),
        rs.getObject("converted_note_id", UUID.class),
        rs.getObject("converted_idea_id", UUID.class),
        rs.getTimestamp("created_at"),
        rs.getTimestamp("updated_at")
    )).list();
}
```

- [ ] **Step 6: Add query service and controller**

```java
public class TrendInboxQueryService {

    private final TrendItemRepository trendItemRepository;

    public List<TrendItemRepository.TrendItemRecord> list(ListInboxQuery query) {
        TrendItemStatus status = query.status() == null
            ? TrendItemStatus.ANALYZED
            : TrendItemStatus.valueOf(query.status());
        TrendSourceType sourceType = query.sourceType() == null
            ? null
            : TrendSourceType.valueOf(query.sourceType());
        return trendItemRepository.listInbox(query.userId(), status, sourceType);
    }

    public record ListInboxQuery(UUID userId, String status, String sourceType) {
    }
}
```

```java
@RestController
@RequestMapping("/api/v1/trends")
public class TrendInboxController {

    private final TrendInboxQueryService trendInboxQueryService;

    @GetMapping("/inbox")
    public ApiEnvelope<List<TrendInboxItemResponse>> listInbox(@RequestParam("user_id") String userId,
                                                               @RequestParam(value = "status", required = false) String status,
                                                               @RequestParam(value = "source_type", required = false) String sourceType) {
        List<TrendInboxItemResponse> response = trendInboxQueryService.list(
                new TrendInboxQueryService.ListInboxQuery(UUID.fromString(userId), status, sourceType)
            )
            .stream()
            .map(TrendInboxItemResponse::from)
            .toList();
        return ApiEnvelope.success(null, response);
    }
}
```

- [ ] **Step 7: Run the backend query tests and verify they pass**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendInboxControllerTest,JdbcTrendItemRepositoryIntegrationTest
```

Expected: PASS for the new list query path and repository ordering/filter behavior.

- [ ] **Step 8: Commit**

```bash
git add \
  server/src/main/java/com/noteops/agent/controller/trend/TrendInboxController.java \
  server/src/main/java/com/noteops/agent/service/trend/TrendInboxQueryService.java \
  server/src/main/java/com/noteops/agent/repository/trend/TrendItemRepository.java \
  server/src/main/java/com/noteops/agent/repository/trend/JdbcTrendItemRepository.java \
  server/src/test/java/com/noteops/agent/controller/trend/TrendInboxControllerTest.java \
  server/src/test/java/com/noteops/agent/repository/trend/JdbcTrendItemRepositoryIntegrationTest.java
git commit -m "feat: add trend inbox query API"
```

### Task 2: Add real `IGNORE` backend action with trace, tool log, and user event

**Files:**
- Create: `server/src/main/java/com/noteops/agent/controller/trend/TrendActionController.java`
- Create: `server/src/main/java/com/noteops/agent/service/trend/TrendActionApplicationService.java`
- Create: `server/src/test/java/com/noteops/agent/controller/trend/TrendActionControllerTest.java`
- Create: `server/src/test/java/com/noteops/agent/service/trend/TrendActionApplicationServiceTest.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/trend/TrendActionRequest.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/trend/TrendActionResponse.java`

- [ ] **Step 1: Write the failing service test for `IGNORE`**

```java
@Test
void ignoresTrendItemAndAppendsGovernanceRecords() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID trendItemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UUID traceId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    TrendItemRecord analyzed = new TrendItemRecord(
        trendItemId,
        userId,
        TrendSourceType.GITHUB,
        "repo#123",
        "OpenAI adds workflow primitives",
        "https://example.com",
        "Summary",
        0.92,
        TrendAnalysisPayload.fromMap(Map.of(
            "summary", "Summary",
            "why_it_matters", "Affects workflows",
            "topic_tags", List.of("agent"),
            "signal_type", "PRODUCT",
            "note_worthy", true,
            "idea_worthy", false,
            "suggested_action", "SAVE_AS_NOTE",
            "reasoning_summary", "Useful"
        )),
        Map.of(),
        TrendItemStatus.ANALYZED,
        TrendActionType.SAVE_AS_NOTE,
        Instant.parse("2026-04-11T10:00:00Z"),
        Instant.parse("2026-04-11T10:00:00Z"),
        null,
        null,
        Instant.parse("2026-04-11T10:00:00Z"),
        Instant.parse("2026-04-11T10:00:00Z")
    );
    TrendItemRecord ignored = new TrendItemRecord(
        trendItemId,
        userId,
        analyzed.sourceType(),
        analyzed.sourceItemKey(),
        analyzed.title(),
        analyzed.url(),
        analyzed.summary(),
        analyzed.normalizedScore(),
        analyzed.analysisPayload(),
        analyzed.extraAttributes(),
        TrendItemStatus.IGNORED,
        TrendActionType.IGNORE,
        analyzed.sourcePublishedAt(),
        analyzed.lastIngestedAt(),
        null,
        null,
        analyzed.createdAt(),
        Instant.parse("2026-04-11T10:05:00Z")
    );
    when(agentTraceRepository.create(
        eq(userId),
        eq("TREND_ACTION"),
        eq("Ignore trend item"),
        eq("TREND_ITEM"),
        eq(trendItemId),
        anyList(),
        anyMap()
    )).thenReturn(traceId);
    when(trendItemRepository.findByIdAndUserId(trendItemId, userId)).thenReturn(Optional.of(analyzed));
    when(trendItemRepository.updateStatus(trendItemId, userId, TrendItemStatus.IGNORED, TrendActionType.IGNORE, null, null))
        .thenReturn(ignored);

    TrendActionApplicationService.ActionResult result = service.act(
        new TrendActionApplicationService.ActionCommand(trendItemId, userId, TrendActionType.IGNORE, null)
    );

    assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.IGNORED);
    verify(toolInvocationLogRepository).append(eq(userId), eq(traceId), eq("trend.item.ignore"), eq("COMPLETED"), anyMap(), anyMap(), isNull(), isNull(), isNull());
    verify(userActionEventRepository).append(eq(userId), eq("TREND_ITEM_IGNORED"), eq("TREND_ITEM"), eq(trendItemId), eq(traceId), anyMap());
    verify(agentTraceRepository).markCompleted(eq(traceId), eq("Trend item ignored"), anyMap());
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionApplicationServiceTest
```

Expected: FAIL because the application service does not exist.

- [ ] **Step 3: Write the failing controller test for action endpoint**

```java
@WebMvcTest(TrendActionController.class)
@Import(ApiExceptionHandler.class)
class TrendActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrendActionApplicationService trendActionApplicationService;

    @Test
    void ignoresTrendItemThroughActionEndpoint() throws Exception {
        UUID trendItemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(trendActionApplicationService.act(any()))
            .thenReturn(new TrendActionApplicationService.ActionResult(
                UUID.fromString("33333333-3333-3333-3333-333333333333").toString(),
                new TrendItemRepository.TrendItemRecord(
                    trendItemId, userId, TrendSourceType.GITHUB, "repo#123", "Title", "https://example.com",
                    "Summary", 0.92, TrendAnalysisPayload.empty(), Map.of(), TrendItemStatus.IGNORED,
                    TrendActionType.IGNORE, Instant.now(), Instant.now(), null, null, Instant.now(), Instant.now()
                )
            ));

        mockMvc.perform(post("/api/v1/trends/{trendItemId}/actions", trendItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "action": "IGNORE",
                      "operator_note": "Not relevant"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.trend_item_id").value(trendItemId.toString()))
            .andExpect(jsonPath("$.data.action_result").value("IGNORED"));
    }
}
```

- [ ] **Step 4: Run the controller test to verify it fails**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionControllerTest
```

Expected: FAIL because the action controller does not exist yet.

- [ ] **Step 5: Implement the action service**

```java
public class TrendActionApplicationService {

    public ActionResult act(ActionCommand command) {
        if (command.action() != TrendActionType.IGNORE) {
            throw new ApiException(HttpStatus.CONFLICT, "TREND_ACTION_DEFERRED", "Action is deferred to Step 4.6", null);
        }

        TrendItemRecord existing = trendItemRepository.findByIdAndUserId(command.trendItemId(), command.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TREND_ITEM_NOT_FOUND", "Trend item not found", null));

        UUID traceId = agentTraceRepository.create(
            command.userId(),
            "TREND_ACTION",
            "Ignore trend item",
            "TREND_ITEM",
            existing.id(),
            List.of("TrendActionApplicationService"),
            Map.of("trend_item_id", existing.id(), "action", command.action().name())
        );

        toolInvocationLogRepository.append(
            command.userId(),
            traceId,
            "trend.item.ignore",
            "STARTED",
            Map.of("trend_item_id", existing.id(), "action", command.action().name()),
            Map.of(),
            null,
            null,
            null
        );

        TrendItemRecord updated = trendItemRepository.updateStatus(
            existing.id(),
            command.userId(),
            TrendItemStatus.IGNORED,
            TrendActionType.IGNORE,
            null,
            null
        );

        toolInvocationLogRepository.append(
            command.userId(),
            traceId,
            "trend.item.ignore",
            "COMPLETED",
            Map.of("trend_item_id", existing.id(), "action", command.action().name()),
            Map.of("status", updated.status().name()),
            null,
            null,
            null
        );
        userActionEventRepository.append(
            command.userId(),
            "TREND_ITEM_IGNORED",
            "TREND_ITEM",
            updated.id(),
            traceId,
            Map.of("trend_item_id", updated.id(), "action", command.action().name(), "operator_note", command.operatorNote())
        );
        agentTraceRepository.markCompleted(traceId, "Trend item ignored", Map.of("trend_item_id", updated.id(), "status", updated.status().name()));
        return new ActionResult(traceId.toString(), updated);
    }

    public record ActionCommand(UUID trendItemId, UUID userId, TrendActionType action, String operatorNote) {
    }

    public record ActionResult(String traceId, TrendItemRecord trendItem) {
    }
}
```

- [ ] **Step 6: Implement the action controller and response mapping**

```java
public record TrendActionRequest(
    @JsonProperty("user_id")
    UUID userId,
    String action,
    @JsonProperty("operator_note")
    String operatorNote
) {
}
```

```java
@RestController
@RequestMapping("/api/v1/trends")
public class TrendActionController {

    private final TrendActionApplicationService trendActionApplicationService;

    @PostMapping("/{trendItemId}/actions")
    public ApiEnvelope<TrendActionResponse> act(@PathVariable String trendItemId,
                                                @RequestBody TrendActionRequest request) {
        TrendActionApplicationService.ActionResult result = trendActionApplicationService.act(
            new TrendActionApplicationService.ActionCommand(
                UUID.fromString(trendItemId),
                request.userId(),
                TrendActionType.valueOf(request.action()),
                request.operatorNote()
            )
        );
        return ApiEnvelope.success(
            result.traceId(),
            new TrendActionResponse(
                result.trendItem().id().toString(),
                result.trendItem().status().name(),
                result.trendItem().convertedNoteId() == null ? null : result.trendItem().convertedNoteId().toString(),
                result.trendItem().convertedIdeaId() == null ? null : result.trendItem().convertedIdeaId().toString()
            )
        );
    }
}
```

- [ ] **Step 7: Run the action tests and verify they pass**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionApplicationServiceTest,TrendActionControllerTest
```

Expected: PASS with `IGNORE` closing the backend action loop and deferred actions still rejected.

- [ ] **Step 8: Commit**

```bash
git add \
  server/src/main/java/com/noteops/agent/controller/trend/TrendActionController.java \
  server/src/main/java/com/noteops/agent/service/trend/TrendActionApplicationService.java \
  server/src/main/java/com/noteops/agent/dto/trend/TrendActionRequest.java \
  server/src/main/java/com/noteops/agent/dto/trend/TrendActionResponse.java \
  server/src/test/java/com/noteops/agent/controller/trend/TrendActionControllerTest.java \
  server/src/test/java/com/noteops/agent/service/trend/TrendActionApplicationServiceTest.java
git commit -m "feat: add trend ignore action"
```

### Task 3: Add tested navigation state and move `Capture` to `Home`

**Files:**
- Create: `web/src/navigationState.ts`
- Create: `web/src/homeState.ts`
- Create: `web/src/views/HomeView.tsx`
- Create: `web/src/views/NotesView.tsx`
- Create: `web/src/views/IdeasView.tsx`
- Create: `web/src/views/WorkspaceView.tsx`
- Create: `web/tests/navigationState.test.ts`
- Create: `web/tests/homeState.test.ts`
- Modify: `web/src/App.tsx`
- Modify: `web/src/styles.css`

- [ ] **Step 1: Write the failing navigation helper tests**

```ts
import test from "node:test";
import assert from "node:assert/strict";

import {
  deriveInitialView,
  toHash,
  resolvePostCaptureNavigation
} from "../src/navigationState.ts";

test("deriveInitialView maps supported hashes to app views", () => {
  assert.equal(deriveInitialView("#/"), "HOME");
  assert.equal(deriveInitialView("#/notes"), "NOTES");
  assert.equal(deriveInitialView("#/ideas"), "IDEAS");
  assert.equal(deriveInitialView("#/workspace"), "WORKSPACE");
  assert.equal(deriveInitialView("#/trends"), "TRENDS");
});

test("resolvePostCaptureNavigation sends successful capture to notes detail", () => {
  assert.deepEqual(
    resolvePostCaptureNavigation("note-123"),
    { nextView: "NOTES", nextSelectedNoteId: "note-123" }
  );
});

test("toHash returns canonical hashes", () => {
  assert.equal(toHash("HOME"), "#/");
  assert.equal(toHash("TRENDS"), "#/trends");
});
```

- [ ] **Step 2: Run the navigation tests to verify they fail**

Run:

```bash
cd web && node --test tests/navigationState.test.ts
```

Expected: FAIL because `navigationState.ts` does not exist.

- [ ] **Step 3: Write the failing home summary helper tests**

```ts
import test from "node:test";
import assert from "node:assert/strict";

import { buildHomeSummaryCards } from "../src/homeState.ts";

test("buildHomeSummaryCards returns counts for all four entry domains", () => {
  assert.deepEqual(
    buildHomeSummaryCards({
      notesCount: 12,
      ideasCount: 5,
      todayItemsCount: 7,
      trendCount: 9
    }),
    [
      { key: "NOTES", label: "Notes", count: 12 },
      { key: "IDEAS", label: "Ideas", count: 5 },
      { key: "WORKSPACE", label: "Today Items", count: 7 },
      { key: "TRENDS", label: "Trends", count: 9 }
    ]
  );
});
```

- [ ] **Step 4: Run the home helper test to verify it fails**

Run:

```bash
cd web && node --test tests/homeState.test.ts
```

Expected: FAIL because `homeState.ts` does not exist.

- [ ] **Step 5: Implement the helper modules**

```ts
export type AppView = "HOME" | "NOTES" | "IDEAS" | "WORKSPACE" | "TRENDS";

const HASH_TO_VIEW: Record<string, AppView> = {
  "#/": "HOME",
  "#/notes": "NOTES",
  "#/ideas": "IDEAS",
  "#/workspace": "WORKSPACE",
  "#/trends": "TRENDS"
};

export function deriveInitialView(hash: string): AppView {
  return HASH_TO_VIEW[hash] ?? "HOME";
}

export function toHash(view: AppView): string {
  return Object.entries(HASH_TO_VIEW).find(([, currentView]) => currentView === view)?.[0] ?? "#/";
}

export function resolvePostCaptureNavigation(noteId: string | null): { nextView: AppView; nextSelectedNoteId: string | null } {
  return {
    nextView: noteId ? "NOTES" : "HOME",
    nextSelectedNoteId: noteId
  };
}
```

```ts
export function buildHomeSummaryCards(input: {
  notesCount: number;
  ideasCount: number;
  todayItemsCount: number;
  trendCount: number;
}) {
  return [
    { key: "NOTES", label: "Notes", count: input.notesCount },
    { key: "IDEAS", label: "Ideas", count: input.ideasCount },
    { key: "WORKSPACE", label: "Today Items", count: input.todayItemsCount },
    { key: "TRENDS", label: "Trends", count: input.trendCount }
  ] as const;
}
```

- [ ] **Step 6: Split the app shell into focused views and move capture to `Home`**

```tsx
const [activeView, setActiveView] = useState<AppView>(() => deriveInitialView(window.location.hash || "#/"));

useEffect(() => {
  function handleHashChange() {
    setActiveView(deriveInitialView(window.location.hash || "#/"));
  }
  window.addEventListener("hashchange", handleHashChange);
  return () => window.removeEventListener("hashchange", handleHashChange);
}, []);

function navigate(nextView: AppView) {
  window.location.hash = toHash(nextView);
}

async function handleCaptureSubmit(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
  const result = await createCapture({ userId: activeUserId, sourceType: captureSourceType, rawText, sourceUrl, titleHint });
  await refreshNotes(result.note_id ?? undefined);
  const followUp = resolvePostCaptureNavigation(result.note_id);
  setSelectedNoteId(followUp.nextSelectedNoteId);
  navigate(followUp.nextView);
}
```

```tsx
{activeView === "HOME" ? (
  <HomeView
    summaryCards={buildHomeSummaryCards({
      notesCount: notes.length,
      ideasCount: ideas.length,
      todayItemsCount: reviewsToday.length + tasksToday.length,
      trendCount: trendInboxItems.length
    })}
    onNavigate={navigate}
    captureState={{
      sourceType: captureSourceType,
      rawText,
      sourceUrl,
      titleHint,
      isSubmitting: isCapturing,
      error: captureError,
      result: captureResult
    }}
    onCaptureSubmit={handleCaptureSubmit}
  />
) : null}
```

- [ ] **Step 7: Run helper tests and the web build**

Run:

```bash
cd web && node --test tests/navigationState.test.ts tests/homeState.test.ts tests/ideaWorkspaceState.test.ts
cd web && npm run build
```

Expected: PASS with navigation helpers green and the app building after the view split.

- [ ] **Step 8: Commit**

```bash
git add \
  web/src/navigationState.ts \
  web/src/homeState.ts \
  web/src/views/HomeView.tsx \
  web/src/views/NotesView.tsx \
  web/src/views/IdeasView.tsx \
  web/src/views/WorkspaceView.tsx \
  web/tests/navigationState.test.ts \
  web/tests/homeState.test.ts \
  web/src/App.tsx \
  web/src/styles.css
git commit -m "feat: add lightweight home and navigation shell"
```

### Task 4: Add `Trend Inbox` web page and wire the real `IGNORE` action

**Files:**
- Create: `web/src/trendInboxState.ts`
- Create: `web/src/views/TrendInboxView.tsx`
- Create: `web/tests/trendInboxState.test.ts`
- Modify: `web/src/api.ts`
- Modify: `web/src/types.ts`
- Modify: `web/src/App.tsx`
- Modify: `web/src/styles.css`

- [ ] **Step 1: Write the failing trend inbox state tests**

```ts
import test from "node:test";
import assert from "node:assert/strict";

import { applyIgnoreActionResult, defaultTrendInboxQuery } from "../src/trendInboxState.ts";

test("defaultTrendInboxQuery targets analyzed items", () => {
  assert.deepEqual(defaultTrendInboxQuery(), {
    status: "ANALYZED",
    sourceType: "ALL"
  });
});

test("applyIgnoreActionResult updates only the acted-on item status", () => {
  const next = applyIgnoreActionResult(
    [
      { trend_item_id: "trend-1", status: "ANALYZED" },
      { trend_item_id: "trend-2", status: "ANALYZED" }
    ],
    { trend_item_id: "trend-2", action_result: "IGNORED" }
  );

  assert.deepEqual(next, [
    { trend_item_id: "trend-1", status: "ANALYZED" },
    { trend_item_id: "trend-2", status: "IGNORED" }
  ]);
});
```

- [ ] **Step 2: Run the trend inbox state test to verify it fails**

Run:

```bash
cd web && node --test tests/trendInboxState.test.ts
```

Expected: FAIL because `trendInboxState.ts` does not exist.

- [ ] **Step 3: Add trend API types and request helpers**

```ts
export type TrendInboxItem = {
  trend_item_id: string;
  user_id: string;
  source_type: string;
  source_item_key: string;
  title: string;
  url: string;
  summary: string | null;
  normalized_score: number;
  status: string;
  suggested_action: string | null;
  analysis_payload: Record<string, unknown>;
  source_published_at: string | null;
  last_ingested_at: string | null;
  updated_at: string;
};

export type TrendActionResult = {
  trend_item_id: string;
  action_result: string;
  converted_note_id: string | null;
  converted_idea_id: string | null;
};
```

```ts
export function listTrendInbox(userId: string, status = "ANALYZED", sourceType?: string): Promise<TrendInboxItem[]> {
  const params = new URLSearchParams({ user_id: userId, status });
  if (sourceType && sourceType !== "ALL") {
    params.set("source_type", sourceType);
  }
  return request(`/api/v1/trends/inbox?${params.toString()}`);
}

export function actOnTrendItem(trendItemId: string, input: {
  userId: string;
  action: "IGNORE" | "SAVE_AS_NOTE" | "PROMOTE_TO_IDEA";
  operatorNote?: string;
}): Promise<TrendActionResult> {
  return request(`/api/v1/trends/${encodeURIComponent(trendItemId)}/actions`, {
    method: "POST",
    body: JSON.stringify({
      user_id: input.userId,
      action: input.action,
      operator_note: input.operatorNote ?? null
    })
  });
}
```

- [ ] **Step 4: Implement the trend inbox state helper**

```ts
export function defaultTrendInboxQuery() {
  return {
    status: "ANALYZED",
    sourceType: "ALL"
  } as const;
}

export function applyIgnoreActionResult<T extends { trend_item_id: string; status: string }>(
  items: T[],
  result: { trend_item_id: string; action_result: string }
) {
  return items.map((item) =>
    item.trend_item_id === result.trend_item_id
      ? { ...item, status: result.action_result }
      : item
  );
}
```

- [ ] **Step 5: Build the `Trend Inbox` page**

```tsx
export function TrendInboxView(props: {
  items: TrendInboxItem[];
  isLoading: boolean;
  error: string | null;
  onIgnore: (trendItemId: string) => Promise<void>;
}) {
  if (props.isLoading) {
    return <section className="trend-page"><p className="status-message">加载 Trend Inbox...</p></section>;
  }
  if (props.error) {
    return <section className="trend-page"><p className="status-message error">{props.error}</p></section>;
  }
  if (props.items.length === 0) {
    return <section className="trend-page"><p className="status-message">当前没有可处理的趋势候选。</p></section>;
  }

  return (
    <section className="trend-page">
      <header className="trend-page-header">
        <div>
          <p className="panel-kicker">Trend Inbox</p>
          <h1>Actionable External Signals</h1>
          <p className="section-hint">这里只处理趋势候选，不混入 Notes / Ideas / Workspace。</p>
        </div>
        <span className="meta-chip">{props.items.length} analyzed</span>
      </header>

      <div className="trend-list">
        {props.items.map((item) => (
          <article key={item.trend_item_id} className="trend-card">
            <div className="trend-card-header">
              <div>
                <h2>{item.title}</h2>
                <p className="trend-meta">{item.source_type} · score {item.normalized_score.toFixed(2)} · {item.suggested_action ?? "NO_ACTION"}</p>
              </div>
              <span className="meta-chip">{item.status}</span>
            </div>
            <p className="trend-summary">{item.summary ?? "暂无摘要"}</p>
            <div className="trend-actions">
              <button type="button" className="primary-button" onClick={() => props.onIgnore(item.trend_item_id)}>Ignore</button>
              <button type="button" className="ghost-button" disabled>Save as Note</button>
              <button type="button" className="ghost-button" disabled>Promote to Idea</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 6: Wire the page into `App.tsx` with local refresh on ignore**

```tsx
const [trendInboxItems, setTrendInboxItems] = useState<TrendInboxItem[]>([]);
const [trendInboxError, setTrendInboxError] = useState<string | null>(null);
const [isLoadingTrendInbox, setIsLoadingTrendInbox] = useState(false);

async function refreshTrendInbox(userId: string) {
  setIsLoadingTrendInbox(true);
  try {
    const items = await listTrendInbox(userId);
    setTrendInboxItems(items);
    setTrendInboxError(null);
  } catch (error) {
    setTrendInboxError(error instanceof Error ? error.message : "加载 Trend Inbox 失败");
  } finally {
    setIsLoadingTrendInbox(false);
  }
}

async function handleIgnoreTrendItem(trendItemId: string) {
  const result = await actOnTrendItem(trendItemId, { userId: activeUserId, action: "IGNORE" });
  setTrendInboxItems((current) => applyIgnoreActionResult(current, result));
}
```

- [ ] **Step 7: Run trend inbox tests and web build**

Run:

```bash
cd web && node --test tests/navigationState.test.ts tests/homeState.test.ts tests/trendInboxState.test.ts tests/ideaWorkspaceState.test.ts
cd web && npm run build
```

Expected: PASS with the new Trend Inbox page loading from the real API and `IGNORE` updating local state.

- [ ] **Step 8: Commit**

```bash
git add \
  web/src/trendInboxState.ts \
  web/src/views/TrendInboxView.tsx \
  web/tests/trendInboxState.test.ts \
  web/src/api.ts \
  web/src/types.ts \
  web/src/App.tsx \
  web/src/styles.css
git commit -m "feat: add trend inbox web page"
```

### Task 5: Sync docs and run final verification

**Files:**
- Modify: `docs/codex/Documentation.md`

- [ ] **Step 1: Update Phase 4 docs to reflect Step 4.5**

```md
### 1.5 Step 4.5 当前落地状态

当前仓库已完成 Step 4.5 的最小 Trend Inbox 闭环：
- 已提供 `GET /api/v1/trends/inbox`
- Web 已提供轻首页 `Home`
- `Notes` / `Ideas` / `Workspace` / `Trend Inbox` 已拆成独立主页面
- `Capture` 已迁移到 `Home`
- `Capture` 成功后会跳转到 `Notes` 并自动打开新 Note 详情
- `Trend Inbox` 当前只真实支持 `IGNORE`
- `SAVE_AS_NOTE` / `PROMOTE_TO_IDEA` 仍 deferred 到 Step 4.6
```

- [ ] **Step 2: Run the narrow backend test suite**

Run:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendInboxControllerTest,TrendActionControllerTest,TrendActionApplicationServiceTest,JdbcTrendItemRepositoryIntegrationTest,TrendPlanApplicationServiceTest,TrendPlanControllerTest
```

Expected: PASS with Step 4.4 regressions still green.

- [ ] **Step 3: Run the web verification**

Run:

```bash
cd web && node --test tests/navigationState.test.ts tests/homeState.test.ts tests/trendInboxState.test.ts tests/ideaWorkspaceState.test.ts
cd web && npm run build
```

Expected: PASS with navigation helpers, home summary logic, and Trend Inbox state logic all green.

- [ ] **Step 4: Manual verification**

Run the local app and verify:

```text
1. Open Home and confirm only summary cards, entry cards, and Capture are visible.
2. Submit a Capture and confirm the app lands on Notes with the new Note detail open.
3. Open Trend Inbox and confirm only trend cards are visible.
4. Click Ignore on one trend card and confirm only that card updates to IGNORED.
5. Confirm Save as Note and Promote to Idea remain disabled.
```

- [ ] **Step 5: Commit**

```bash
git add docs/codex/Documentation-phase4.md
git commit -m "docs: sync trend inbox step 4.5 status"
```

---

## Self-Review

### Spec coverage

Spec coverage is complete when these mappings hold:

1. `Home` light entry page: Task 3
2. `Capture` moved to `Home` and post-success jump to `Notes`: Task 3
3. Independent `Notes` / `Ideas` / `Workspace` / `Trend Inbox` views: Task 3 and Task 4
4. `GET /api/v1/trends/inbox`: Task 1
5. Real `IGNORE` action with trace/log/event: Task 2
6. Step 4.6 actions still deferred: Task 2 and Task 4
7. Step 4.5 doc sync: Task 5

### Placeholder scan

Search before implementation:

```bash
rg -n "TODO|TBD|implement later|appropriate|edge cases|similar to" docs/superpowers/plans/2026-04-11-trend-inbox-navigation-implementation.md | rg -v "Placeholder scan"
```

Expected: no matches.

### Type consistency checks

Check these names stay identical across tasks:

1. `TrendInboxQueryService.ListInboxQuery`
2. `TrendActionApplicationService.ActionCommand`
3. `TrendActionApplicationService.ActionResult`
4. `TrendInboxItem`
5. `TrendActionResult`
6. `AppView`
7. `resolvePostCaptureNavigation`
8. `applyIgnoreActionResult`
