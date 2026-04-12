# Trend Idea Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Step 4.6B so `PROMOTE_TO_IDEA` creates a real Idea from an analyzed Trend item, preserves provenance and governance logs, and routes the user into the existing Ideas detail flow without auto-assessing.

**Architecture:** Add a dedicated `TrendIdeaConversionService` that owns the conversion transaction, while `TrendActionApplicationService` remains the command entrypoint. Extend the Idea source model with `FROM_TREND` and a provenance field so the existing Idea repository/controller can serve Trend-derived Ideas without new browsing flows. Keep the Trend action API stable and reuse the current response envelope, then let the existing Ideas UI open the created Idea detail.

**Tech Stack:** Java 23, Spring Boot, JDBC, H2 integration tests, JUnit 5, Mockito, React + TypeScript, Vite.

---

### Task 1: Freeze the Trend -> Idea data model

**Files:**
- Modify: `server/src/main/java/com/noteops/agent/model/idea/IdeaSourceMode.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/idea/IdeaRepository.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/idea/JdbcIdeaRepository.java`
- Modify: `server/src/main/java/com/noteops/agent/service/idea/IdeaQueryService.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/idea/IdeaResponse.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/idea/IdeaDetailResponse.java`
- Modify: `server/src/main/resources/db/migration/V2__create_ideas_table.sql`
- Modify: `server/src/main/resources/db/migration/V3__rename_idea_source_mode_to_manual.sql`
- Create: `server/src/main/resources/db/migration/V5__add_trend_idea_source_columns.sql`
- Test: `server/src/test/java/com/noteops/agent/repository/idea/JdbcIdeaRepositoryIntegrationTest.java`
- Test: `server/src/test/java/com/noteops/agent/controller/idea/IdeaControllerTest.java`

- [ ] **Step 1: Write the failing test**

Add a repository test that inserts a Trend-derived Idea and asserts it round-trips with:

```java
IdeaRepository.IdeaRecord created = ideaRepository.create(
    userId,
    IdeaSourceMode.FROM_TREND,
    null,
    trendItemId,
    "Trend title",
    "Trend summary",
    IdeaStatus.CAPTURED,
    null
);
```

Assert `created.sourceMode()` is `FROM_TREND`, `created.sourceTrendItemId()` matches the Trend id, and `IdeaController#get` returns `source_mode = FROM_TREND` plus `source_trend_item_id`.

- [ ] **Step 2: Run test to verify it fails**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=JdbcIdeaRepositoryIntegrationTest,IdeaControllerTest`

Expected: compile or assertion failure because `FROM_TREND` and `source_trend_item_id` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Add `FROM_TREND` to `IdeaSourceMode`, extend `IdeaRepository.IdeaRecord` with `sourceTrendItemId`, persist it in `JdbcIdeaRepository`, and surface it through `IdeaQueryService`, `IdeaResponse`, and `IdeaDetailResponse`. Add `source_trend_item_id` to the `ideas` table schema and migration.

```java
public enum IdeaSourceMode {
    FROM_NOTE,
    MANUAL,
    FROM_TREND
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=JdbcIdeaRepositoryIntegrationTest,IdeaControllerTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/noteops/agent/model/idea/IdeaSourceMode.java server/src/main/java/com/noteops/agent/repository/idea/IdeaRepository.java server/src/main/java/com/noteops/agent/repository/idea/JdbcIdeaRepository.java server/src/main/java/com/noteops/agent/dto/idea/IdeaResponse.java server/src/main/resources/db/migration/V2__create_ideas_table.sql server/src/main/resources/db/migration/V3__rename_idea_source_mode_to_manual.sql server/src/main/resources/db/migration/V5__add_trend_idea_source_columns.sql server/src/test/java/com/noteops/agent/repository/idea/JdbcIdeaRepositoryIntegrationTest.java server/src/test/java/com/noteops/agent/controller/idea/IdeaControllerTest.java
git commit -m "feat: add trend idea source metadata"
```

### Task 2: Implement TrendIdeaConversionService

**Files:**
- Create: `server/src/main/java/com/noteops/agent/service/trend/TrendIdeaConversionService.java`
- Modify: `server/src/main/java/com/noteops/agent/service/trend/TrendActionApplicationService.java`
- Modify: `server/src/main/java/com/noteops/agent/dto/trend/TrendActionResponse.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/TrendItemRepository.java`
- Modify: `server/src/main/java/com/noteops/agent/repository/trend/JdbcTrendItemRepository.java`
- Test: `server/src/test/java/com/noteops/agent/service/trend/TrendActionApplicationServiceTest.java`
- Test: `server/src/test/java/com/noteops/agent/service/trend/TrendActionControllerTest.java`
- Test: `server/src/test/java/com/noteops/agent/service/trend/TrendIdeaConversionServiceIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

Add a service test that passes an actionable Trend item to `TrendActionApplicationService.act(...)` with `action = PROMOTE_TO_IDEA` and asserts:

```java
assertThat(result.trendItem().status()).isEqualTo(TrendItemStatus.PROMOTED_TO_IDEA);
assertThat(result.trendItem().convertedIdeaId()).isNotNull();
```

Also add an integration test that creates a Trend item in `ANALYZED`, invokes the conversion service, and then reads the Idea back through `IdeaController`.

- [ ] **Step 2: Run test to verify it fails**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionApplicationServiceTest,TrendActionControllerTest,TrendIdeaConversionServiceIntegrationTest`

Expected: FAIL because `PROMOTE_TO_IDEA` still returns deferred and the conversion service does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `TrendIdeaConversionService` with a transactional `promoteToIdea(...)` method. It should:

```java
IdeaRepository.IdeaRecord idea = ideaRepository.create(
    userId,
    IdeaSourceMode.FROM_TREND,
    null,
    trendItem.id(),
    title,
    rawDescription,
    IdeaStatus.CAPTURED,
    null
);
```

Then update the Trend item to `PROMOTED_TO_IDEA`, set `converted_idea_id`, append `tool_invocation_logs`, `user_action_events`, and mark the trace completed. Update `TrendActionApplicationService` to delegate `PROMOTE_TO_IDEA` to the new service and return the updated record through `TrendActionResponse`.

- [ ] **Step 4: Run test to verify it passes**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionApplicationServiceTest,TrendActionControllerTest,TrendIdeaConversionServiceIntegrationTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/noteops/agent/service/trend/TrendIdeaConversionService.java server/src/main/java/com/noteops/agent/service/trend/TrendActionApplicationService.java server/src/main/java/com/noteops/agent/dto/trend/TrendActionResponse.java server/src/main/java/com/noteops/agent/repository/trend/TrendItemRepository.java server/src/main/java/com/noteops/agent/repository/trend/JdbcTrendItemRepository.java server/src/test/java/com/noteops/agent/service/trend/TrendActionApplicationServiceTest.java server/src/test/java/com/noteops/agent/controller/trend/TrendActionControllerTest.java server/src/test/java/com/noteops/agent/service/trend/TrendIdeaConversionServiceIntegrationTest.java
git commit -m "feat: promote trends to ideas"
```

### Task 3: Wire the Ideas page handoff

**Files:**
- Modify: `web/src/App.tsx`
- Modify: `web/src/api.ts`
- Modify: `web/src/types.ts`
- Modify: `web/src/views/TrendInboxView.tsx`
- Modify: `web/src/views/IdeasView.tsx`
- Test: `web/tests/trendInboxState.test.ts`
- Test: `web/tests/navigationState.test.ts`
- Test: `web/tests/ideaWorkspaceState.test.ts`

- [ ] **Step 1: Write the failing test**

Add a front-end state test that mocks a successful `PROMOTE_TO_IDEA` response with `converted_idea_id` and asserts the app navigates to `#/ideas` and selects the created Idea id.

```ts
expect(window.location.hash).toBe("#/ideas");
expect(selectedIdeaId).toBe(convertedIdeaId);
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test web/tests/navigationState.test.ts web/tests/trendInboxState.test.ts web/tests/ideaWorkspaceState.test.ts`

Expected: FAIL because `PROMOTE_TO_IDEA` is still disabled in the UI flow.

- [ ] **Step 3: Write minimal implementation**

Update the Trend Inbox action model to enable `PROMOTE_TO_IDEA` for `ANALYZED` items, call the action API, and on success navigate to `Ideas` while selecting the returned `converted_idea_id`. Keep `IdeasView` as the existing container for the detail page so no new route structure is introduced.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test web/tests/navigationState.test.ts web/tests/trendInboxState.test.ts web/tests/ideaWorkspaceState.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/App.tsx web/src/api.ts web/src/types.ts web/src/views/TrendInboxView.tsx web/src/views/IdeasView.tsx web/tests/trendInboxState.test.ts web/tests/navigationState.test.ts web/tests/ideaWorkspaceState.test.ts
git commit -m "feat: route promoted trends into ideas"
```

### Task 4: Sync docs and verify the full slice

**Files:**
- Modify: `docs/codex/Documentation.md`
- Modify: `docs/codex/Plan.md`
- Test: `server/src/test/java/com/noteops/agent/service/trend/TrendIdeaConversionServiceIntegrationTest.java`
- Test: `server/src/test/java/com/noteops/agent/controller/idea/IdeaControllerTest.java`

- [ ] **Step 1: Write the failing test**

Add a small regression test or assertion that confirms Trend-derived Ideas remain discoverable through `IdeaController` after the conversion flow and that the documentation text no longer says `PROMOTE_TO_IDEA` is deferred in Step 4.6B.

- [ ] **Step 2: Run test to verify it fails**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendIdeaConversionServiceIntegrationTest,IdeaControllerTest`

Expected: PASS after Tasks 1-3, but keep this step to force a docs/runtime consistency check during implementation.

- [ ] **Step 3: Write minimal implementation**

Update `docs/codex/Documentation.md` to mark Step 4.6A complete and Step 4.6B as the live implementation target once the code lands. Update `docs/codex/Plan.md` if the phase language needs to reflect the new `FROM_TREND` source mode and the `Ideas` handoff.

- [ ] **Step 4: Run test to verify it passes**

Run: `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -q test -Dtest=TrendActionApplicationServiceTest,TrendActionApplicationServiceSaveAsNoteTest,TrendNoteConversionServiceIntegrationTest,TrendActionControllerTest,TrendIdeaConversionServiceIntegrationTest,IdeaControllerTest && npm --prefix /Users/annasun/Developer/Projects/IdeaProjects/NoteOps-Agent/web run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/codex/Documentation.md docs/codex/Plan.md server/src/test/java/com/noteops/agent/service/trend/TrendIdeaConversionServiceIntegrationTest.java server/src/test/java/com/noteops/agent/controller/idea/IdeaControllerTest.java
git commit -m "docs: sync trend idea conversion and phase 4"
```

## Review Checklist

- `IdeaSourceMode.FROM_TREND` is used only for Trend-derived Ideas.
- `PROMOTE_TO_IDEA` does not auto-assess the new Idea.
- `converted_idea_id` is written on the Trend row.
- The new Idea remains visible and operable via the existing Idea pages.
- The plan does not introduce a second Idea browsing UI.
- Existing `SAVE_AS_NOTE` and `IGNORE` behavior is preserved.
