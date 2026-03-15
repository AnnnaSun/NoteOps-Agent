---
name: noteops-schema-guard
description: Guard schema consistency for NoteOps when tasks affect tables, columns, enums, DTOs, entities, migrations, or cross-layer field semantics. Use when a task adds, removes, renames, or changes persisted fields, relationships, states, indexes, or business identifiers. Do not use for pure UI-only work.
---

# Purpose

Prevent drift across migration, persistence, domain model, API contract, frontend usage, and documentation for NoteOps-Agent Phase 1.

# Required context

Read these first:

1. `AGENTS.md`
2. `docs/codex/Prompt.md`
3. `docs/codex/Plan.md`
4. `docs/codex/Implement.md`
5. schema-related docs under `docs/`
6. all changed migration/model/DTO/controller/frontend files relevant to the affected fields

# Trigger conditions

Use this skill whenever the task touches any of the following:

- table creation or alteration
- new/changed migration files
- entity/model fields
- DTO request/response fields
- enum or status values
- foreign keys / unique keys / indexes
- cross-module identifiers such as `note_id`, `review_item_id`, `task_source`, `queue_type`, `target_layer`, `user_id`

# Consistency checklist

## 1. Migration layer

Verify:

- a migration exists when schema changed
- table/column/index names follow repo conventions
- nullability and defaults are explicit
- enum strategy is clear (database enum, varchar + check, or equivalent)
- down/rollback handling is addressed if the repo convention requires it

## 2. Persistence layer

Verify:

- entity/model matches schema exactly
- repository queries still compile and match new fields
- JSONB or flexible fields are mapped intentionally, not accidentally
- relations and foreign keys are reflected correctly

## 3. Domain semantics

Verify the change does not violate frozen business rules:

- `notes` and `note_contents` remain split between current view and append-only raw/history blocks
- `review_states` still supports dual queues and completion semantics
- `tasks` still supports both `SYSTEM` and `USER`
- `change_proposals.target_layer` remains limited to interpretation/metadata/relation semantics
- `user_id` remains present on core aggregates

## 4. API contract layer

Verify:

- request/response DTOs reflect the actual persisted and derived fields
- serialization names match schema/domain naming decisions
- controllers and services use the updated fields consistently
- error responses still make sense if validation changed

## 5. Frontend usage layer

Verify:

- API client payloads still match backend contracts
- forms, displays, and Today views use the correct field names and allowed values
- frontend does not keep stale enum literals or removed fields

## 6. Documentation layer

Verify and update if needed:

- field description docs
- API docs
- state machine docs
- `docs/codex/Documentation.md`

## 7. Validation layer

Run the narrowest checks that prove consistency:

- migration execution or schema validation
- affected tests
- build / type-check for touched modules

Never assert backward compatibility or successful migration without validation.

# Drift patterns to catch explicitly

Watch for these common failures:

1. migration changed, entity not updated
2. entity changed, DTO not updated
3. backend changed enum/state, frontend still sends old literals
4. `user_id` missing on a new core table
5. proposal fields added but apply/rollback logic not aligned
6. raw note content accidentally becoming mutable through proposal flow
7. docs still describing pre-patch semantics

# Output contract

Return a schema consistency report with exactly these sections:

## Migration
- status and issues

## Persistence
- status and issues

## Contract
- DTO/API consistency status

## Frontend
- usage consistency status

## Docs
- updated / pending mismatches

## Validation
- checks run and remaining risk
