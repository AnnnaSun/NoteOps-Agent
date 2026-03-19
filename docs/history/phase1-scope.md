# Phase 1 Scope

This file is retained as a historical Phase 1 snapshot reference.

- Phase 1 minimal kernel is complete: Capture, Note query, Review, Task, Proposal governance, Trace/Event, and the first minimal Web workspace all landed during the earlier Phase 1 milestones.
- The active repository baseline is no longer Phase 1. Current implementation and milestone tracking now live in `docs/codex/Plan.md` and `docs/codex/Documentation.md`.
- If you need the current product and API boundary, use `docs/codex/Documentation.md` as the source of truth instead of treating this file as the active roadmap.

## Historical Phase 1 Deliverables

- `POST /api/v1/captures`
- `GET /api/v1/captures/{id}`
- `GET /api/v1/notes`
- `GET /api/v1/notes/{id}`
- `GET /api/v1/reviews/today`
- `POST /api/v1/reviews/{review_item_id}/complete`
- `POST /api/v1/tasks`
- `GET /api/v1/tasks/today`
- `POST /api/v1/tasks/{task_id}/complete`
- `POST /api/v1/tasks/{task_id}/skip`
- `POST /api/v1/notes/{note_id}/change-proposals`
- `GET /api/v1/notes/{note_id}/change-proposals`
- `POST /api/v1/notes/{note_id}/change-proposals/{proposal_id}/apply`
- `POST /api/v1/change-proposals/{id}/rollback`

## Historical Notes

- Phase 1 introduced the first `SYSTEM / USER` task split, minimal Review dual-queue semantics, proposal apply / rollback governance, and the original single-page Web surface.
- Phase 2 extends those foundations with Search three-bucket results, workspace aggregation (`Today + Upcoming`), Review completion form semantics, and stricter task creation behavior such as duplicate open user task rejection.
- Search, Idea lifecycle, Trend Inbox, full PWA/offline support, and a complete account system were never part of the active Phase 1 closure and remain governed by the current Phase 2 documentation.
