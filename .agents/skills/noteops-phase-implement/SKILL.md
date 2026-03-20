---
name: noteops-phase-implement
description: Implement the next smallest current-phase milestone for NoteOps-Agent with real end-to-end behavior, narrow validation, and synchronized docs. Use when the user asks to continue the current NoteOps phase rather than inventing a new architecture.
---

# Purpose

Advance the current NoteOps milestone in the smallest coherent slice that produces a real behavior, not placeholder scaffolding.

Current default phase: **Phase 3 = Idea formal closed loop**.

# Current phase scope (Phase 3)

Implement only the smallest coherent slice inside **Idea formal closed loop**.

Primary phase goals:

1. `ideas` schema / enum / state machine alignment
2. independent Idea creation
3. Note -> Idea derivation
4. Idea assess
5. Idea -> Task generation
6. Idea minimal workspace
7. trace / event / user_action_event / structured log alignment
8. documentation sync

Not in scope for the current phase:

- Trend formal closed loop
- full preference profile recomputation
- advanced scoring / ranking
- board / pipeline
- broad future abstractions

# Workflow

## 1. Narrow the request to one slice

Convert broad asks into one smallest deliverable such as:

- Step 3.1 schema / state alignment
- Step 3.2 create + derive Idea
- Step 3.3 Idea list/detail
- Step 3.4 assess Idea
- Step 3.5 Idea -> Task
- Step 3.6 archive / reopen / lifecycle completion
- Step 3.7 governance and observability hardening
- Step 3.8 doc sync and closure

## 2. Protect frozen domain semantics

Always preserve these invariants:

- Note is the first-class aggregate.
- PostgreSQL is the source of truth.
- Raw content is append-only by default.
- Review supports dual queues and completion semantics.
- Task supports both `SYSTEM` and `USER`.
- Proposal acts on `INTERPRETATION`, `METADATA`, or `RELATION`, not raw note body replacement.
- Core tables retain `user_id`.
- Idea follows `CAPTURED -> ASSESSED -> PLANNED -> IN_PROGRESS -> ARCHIVED`.
- Current phase excludes formal Trend / full Preference Learning closed loops.
