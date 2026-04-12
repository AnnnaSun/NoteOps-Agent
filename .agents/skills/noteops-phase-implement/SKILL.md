---
name: noteops-phase-implement
description: Implement the next smallest NoteOps current-phase milestone or sub-step using AGENTS.md and docs/codex/*.md. Use when the task is to continue feature development, complete a planned slice, or deliver a reviewable increment for Trend, Note, Idea conversion, Task, Trace, Web, or migration work. Do not use for pure documentation-only edits or broad unplanned refactors.
---

# Purpose

Execute one minimal, reviewable current-phase implementation slice for NoteOps-Agent without drifting into future phases or unrelated modules.

# Required context

Before changing code, read these files in order:

1. `AGENTS.md`
2. `docs/codex/Prompt.md`
3. `docs/codex/Plan.md`
4. `docs/codex/Implement.md`
5. `docs/codex/Documentation.md`

Then inspect only the code and tests needed for the current milestone.

# When to use

Use this skill when the user asks to:

- continue current phase development
- implement the next milestone
- finish a specific planned feature slice
- build or fix a scoped backend / frontend / database workflow that clearly belongs to an existing milestone

Typical targets for current Phase 4:

- Step 4.1 Trend schema / contract baseline
- Step 4.2 source registry / connector baseline
- Step 4.3 ingest / normalize / dedupe
- Step 4.4 Trend AI analyze contract
- Step 4.5 Trend Inbox
- Step 4.6 Trend -> Note / Idea conversion
- Step 4.7 trace / event / logs / docs closure

# Milestone execution workflow

## 1. Identify the smallest unfinished slice

Choose a slice that:

- belongs to exactly one current milestone, or at most one primary milestone plus an obvious dependency
- can be validated within the touched area
- does not require speculative future-phase behavior

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

- For Trend ingest, it is valid to add source connector, ingest service, trend item persistence, trace write, and a narrow test.
- It is not valid to also build full preference learning or a generic arbitrary crawler platform.

## 4. Protect frozen domain semantics

Always preserve these invariants:

- Note is the first-class aggregate.
- PostgreSQL is the source of truth.
- Raw content is append-only by default.
- External evidence cannot silently overwrite internal knowledge.
- Task must support both `SYSTEM` and `USER`.
- Proposal acts on `INTERPRETATION`, `METADATA`, or `RELATION`, not raw note body replacement.
- Core tables retain `user_id`.
- Trend conversion must keep source lineage.

## 5. Validate narrowly

Run the narrowest meaningful verification for the changed slice:

1. related unit or integration tests
2. build / type check for the touched area
3. migration validation if schema changed
4. manual API / UI sanity check only if needed

Never claim validation that was not run.

## 6. Sync docs when required

If the change affects any of the following, update the relevant docs before finishing:

- schema
- API contract
- state machine
- user-visible workflow
- milestone progress
- default trend plan
- AI analysis contract

At minimum, update `docs/codex/Documentation.md` if milestone status or known risks changed.

# Guardrails

Do not:

- start the next milestone automatically after finishing one slice
- introduce future-phase abstractions “for later”
- silently rename tables, fields, enums, DTOs, or endpoint paths
- bypass trace / event / proposal semantics when the milestone depends on them
- replace append-only raw content with silent overwrite logic
- implement only UI stubs while leaving the backend contract fake for a milestone that requires real persistence
- duplicate Idea assess logic inside Trend instead of reusing Idea flow where appropriate

# Output contract

Return a concise report in this structure:

## 本次完成
- milestone / sub-step completed
- concrete behavior now supported

## 修改文件
- key files changed and why

## 验证结果
- exact commands/tests run
- pass/fail status
- anything not verified

## 风险与下一步
- remaining mismatch or limitation
- best next smallest slice
