---
name: noteops-doc-sync
description: Synchronize NoteOps documentation with implemented code changes. Use when schema, API contracts, milestone status, state machines, workflow semantics, setup commands, or user-visible behavior changed. Do not use for pure internal refactors that have no external or documented effect.
---

# Purpose

Keep NoteOps-Agent documentation aligned with actual implementation so Codex does not drift from the frozen product and architecture baseline.

# Required context

Read before editing docs:

1. `AGENTS.md`
2. `docs/codex/Prompt.md`
3. `docs/codex/Plan.md`
4. `docs/codex/Documentation.md`
5. changed code files
6. nearest relevant docs under `docs/`
7. `README.md` if startup, run flow, or high-level architecture changed

# When to use

Use this skill after code changes involving:

- database schema or migration
- endpoint path or request/response fields
- state machine values or transition rules
- Note / Review / Task / Proposal semantics
- milestone completion state
- startup/build/run commands
- repository structure changes

# Documentation workflow

## 1. Inspect the actual change, not the intended change

Read the changed files first. Infer what was truly implemented.

Separate the impact into these categories:

- schema / field meaning
- API contract
- state machine / lifecycle semantics
- user-visible workflow
- developer workflow / project setup
- milestone progress

Only document what is actually present in code.

## 2. Update the narrowest relevant documents

Possible targets include:

- `docs/codex/Documentation.md`
- `README.md`
- schema or API supplemental docs under `docs/`
- milestone notes or implementation status docs

Avoid rewriting large documents when a targeted delta is enough.

## 3. Preserve frozen semantics from source docs

Do not let documentation regress on these points:

- Note-first architecture
- PostgreSQL as source of truth
- append-only raw content
- Review dual queues
- Task supports both `SYSTEM` and `USER`
- Proposal acts on interpretation/metadata/relation rather than silently overwriting raw content
- Phase 1 excludes formal Trend / Idea / Preference Learning closed loops

## 4. Record milestone reality clearly

When a milestone or sub-step changes status, reflect:

- what is completed
- what is still deferred
- what remains risky or partially verified

Prefer precise wording such as “已完成最小闭环” or “仅完成占位流程，未做质量优化” over vague success language.

## 5. Keep docs implementation-facing

Write for repository execution, not marketing.

Good documentation should answer:

- what changed
- why it matters
- what commands or checks verify it
- what is still not covered

# Guardrails

Do not:

- invent endpoints, fields, defaults, or states absent from code
- leave docs at the old behavior after contract changes
- paste large code blocks where behavioral summary is enough
- mark a milestone complete if the required path is still fake or broken
- let README or Codex docs contradict AGENTS.md

# Output contract

Return:

## 已更新文档
- list updated docs

## 对齐内容
- which code/doc behaviors were synchronized

## 未解决风险
- remaining mismatches or areas still needing doc updates
