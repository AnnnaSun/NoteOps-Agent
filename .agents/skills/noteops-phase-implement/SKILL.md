---
name: noteops-phase-implement
description: Implement the next smallest NoteOps current-phase milestone or sub-step using AGENTS.md and docs/codex/*.md. Use when the task is to continue planned feature development, complete a scoped slice, or deliver a reviewable increment for Review, Search, Today Workspace, Task, Proposal, Trace, Web, or migration work. Do not use for pure documentation-only edits or broad unplanned refactors.
---

# Purpose

Execute one minimal, reviewable implementation slice for the **current NoteOps phase** without drifting into future phases or unrelated modules.

Current default phase: **Phase 2 = Review / Search / Today Workspace**.

# Required context

Before changing code, read these files in order:

1. `AGENTS.md`
2. `docs/codex/Prompt.md`
3. `docs/codex/Plan.md`
4. `docs/codex/Implement.md`
5. `docs/codex/Documentation.md`

Then inspect only the code and tests needed for the current sub-step.

# When to use

Use this skill when the user asks to:

- continue current-phase development
- implement the next planned sub-step
- finish a specific planned feature slice
- build or fix a scoped backend/frontend/database workflow that clearly belongs to the current roadmap

Typical Phase 2 targets:

- Step 2.1 Review schema / enum / state machine alignment
- Step 2.2 Review API + recall feedback contract
- Step 2.3 Today / Upcoming aggregation
- Step 2.4 User Task due_at workflow
- Step 2.5 Search three-bucket contract
- Step 2.6 Search evidence / proposal governance
- Step 2.7 Minimal Web integration for Today / Review / Search
- Step 2.8 Docs closure and deferred backlog sync

# Milestone execution workflow

## 1. Identify the smallest unfinished slice

Choose a slice that:

- belongs to exactly one current sub-step, or at most one primary sub-step plus an obvious dependency
- can be validated within the touched area
- does not require speculative Phase 3/4 behavior

If the request is broad, narrow it to the smallest coherent deliverable before coding.

## 2. Inspect existing implementation before editing

Check the nearest relevant files only:

- migration / schema files
- entity / model / repository
- service / orchestration / controller
- DTO / request / response models
- frontend API client / page / component
- test files

Do not create duplicate patterns if an existing pattern already exists.

## 3. Implement the slice end-to-end only where necessary

Within the slice, keep layers aligned:

- schema ↔ entity/model ↔ repository ↔ service ↔ controller ↔ DTO ↔ frontend usage

But do not expand scope just because adjacent modules exist.

Examples:

- For Step 2.3, it is valid to add aggregation query, service flow, controller response, frontend workspace consumption, and a narrow test.
- It is not valid to also build Idea workspace, trend dashboard, or a generic recommendation engine.

## 4. Protect frozen domain semantics

Always preserve these current-phase invariants:

- Note is the first-class aggregate.
- PostgreSQL is the source of truth.
- Raw content is append-only by default.
- Review supports dual queues and completion semantics.
- Recall feedback supports at least self-rating + short note.
- Task must support both `SYSTEM` and `USER`.
- Task supports `due_at` for workspace ordering.
- Search distinguishes `exact_matches`, `related_matches`, and `external_supplements`.
- External supplements do not directly overwrite note interpretation fields.
- Proposal acts on `INTERPRETATION`, `METADATA`, or `RELATION`, not raw note body replacement.
- Core tables retain `user_id`.

## 5. Validate narrowly

Run the narrowest meaningful verification for the changed slice:

1. related unit or integration tests
2. build / type check for the touched area
3. migration validation if schema changed
4. manual API/UI sanity check only if needed

Never claim validation that was not run.

## 6. Sync docs when required

If the change affects any of the following, update the relevant docs before finishing:

- schema
- API contract
- state machine
- user-visible workflow
- step progress
- deferred backlog

At minimum, update `docs/codex/Documentation.md` if step status, deferred items, or known risks changed.

# Guardrails

Do not:

- start the next sub-step automatically after finishing one slice
- introduce future-phase abstractions “for later”
- silently rename tables, fields, enums, DTOs, or endpoint paths
- bypass trace / event / proposal semantics when the slice depends on them
- replace append-only raw content with silent overwrite logic
- implement only UI stubs while leaving the backend contract fake for a slice that requires real persistence
- silently drop a postponed capability; record it in deferred backlog instead

# Output contract

Return a concise report in this structure:

## 本次完成
- current sub-step completed
- concrete behavior now supported

## 修改文件
- key files changed and why

## 验证结果
- exact commands/tests run
- pass/fail status
- anything not verified

## 风险与下一步
- remaining mismatch or limitation
- deferred items recorded
- best next smallest slice
