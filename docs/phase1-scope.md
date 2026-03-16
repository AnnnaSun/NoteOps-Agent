# Phase 1 Scope

- Current state: Phase 1 M5 backend capture, note, review, and task minimal loops are in place.
- Current backend scope: `POST /api/v1/captures`, `GET /api/v1/captures/{id}`, `GET /api/v1/notes`, `GET /api/v1/notes/{id}`, `GET /api/v1/reviews/today`, `POST /api/v1/reviews/{review_item_id}/complete`, `POST /api/v1/tasks`, `GET /api/v1/tasks/today`, `POST /api/v1/tasks/{task_id}/complete`, `POST /api/v1/tasks/{task_id}/skip`.
- Task Today currently supports optional `timezone_offset`, and Review can derive `REVIEW_FOLLOW_UP` system tasks.
- Next step: M6 Proposal / Trace / Event governance loop.
- Not in scope yet: Search, Idea lifecycle, Trend Inbox, PWA or offline enhancements.
