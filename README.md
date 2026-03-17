# NoteOps Agent

Phase 1 monorepo for the NoteOps knowledge kernel. The current repository state has completed the minimal M3-M7 backend and web loops defined in `docs/codex/Plan.md`.

## Structure

- `server`: Spring Boot backend
- `web`: React + Vite + TypeScript frontend
- `docs`: project documentation
- `docker-compose.yml`: local PostgreSQL service

## Start PostgreSQL

```bash
docker compose up -d
```

PostgreSQL example local settings:

- Host: `localhost`
- Port: `5432`
- Database: `noteops`
- Username: `noteops`
- Password: `noteops`

## Start Server

```bash
cd server
mvn spring-boot:run
```

Server runs on `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/api/v1/health
```

## Start Web

```bash
cd web
npm install
npm run dev
```

Web runs on `http://localhost:5173`.

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

## Validation

```bash
cd server
mvn -q test
```

```bash
cd web
npm run build
```

## Current Scope

- Phase 1 currently has M3-M7 minimal loops in place: Capture, Note query, Review, Task, Proposal governance, and a minimal Web workspace.
- Current implemented backend API scope includes:
  - `POST /api/v1/captures`
  - `GET /api/v1/captures/{id}`
  - `GET /api/v1/notes`
  - `GET /api/v1/notes/{id}`
  - `GET /api/v1/reviews/today`
  - `POST /api/v1/reviews/{review_item_id}/complete`
  - `POST /api/v1/tasks`
  - `GET /api/v1/tasks/today`
  - `GET /api/v1/workspace/today`
  - `GET /api/v1/workspace/upcoming`
  - `POST /api/v1/tasks/{task_id}/complete`
  - `POST /api/v1/tasks/{task_id}/skip`
  - `POST /api/v1/notes/{note_id}/change-proposals`
  - `GET /api/v1/notes/{note_id}/change-proposals`
  - `POST /api/v1/notes/{note_id}/change-proposals/{proposal_id}/apply`
  - `POST /api/v1/change-proposals/{id}/rollback`
- Current web workspace supports:
  - explicit `user_id` selection
  - `TEXT / URL` Capture submission
  - Note list and Note detail browsing
  - Today view for Review + Task
  - Proposal list, generate, apply, and rollback
- Backend workspace aggregation now supports `Today + Upcoming`, but the current web UI is still using the existing Today path and does not yet expose an Upcoming page.
- Current implemented minimal governance details include:
  - Task supports `SYSTEM` and `USER`
  - Task Today supports optional `timezone_offset`
  - Review can derive `REVIEW_FOLLOW_UP` system tasks
  - Proposal currently supports only `INTERPRETATION + LOW` via `REFRESH_INTERPRETATION`

## Known Gaps

- URL extraction is still a Phase 1 placeholder implementation and is not a full fetch/extraction pipeline.
- Proposal governance is still limited to `INTERPRETATION + LOW`; `METADATA`, `RELATION`, and higher-risk governance are not implemented.
- There is no complete account system; the current web flow uses explicit `user_id`.
- Search, formal Idea lifecycle, Trend Inbox, full PWA/offline support, and mobile apps are still out of scope.
- Full manual end-to-end browser validation is still pending; current verification is based on backend tests and frontend build.
