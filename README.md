# NoteOps Agent

Phase 2 monorepo for the NoteOps Review / Search / Today Workspace milestone. The current repository state has completed the minimal Phase 2 backend and web loops defined in `docs/codex/Plan.md`.

## Structure

- `server`: Spring Boot backend
  - `controller`: HTTP 入口与响应组装
  - `service`: 用例编排与业务流程
  - `model`: 业务状态、枚举、领域模型
  - `dto`: 请求 / 响应对象
  - `repository`: 数据访问实现
  - `common`: 通用异常、工具和共享基础类型
  - `config`: 配置属性
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

- Phase 1 minimal kernel is complete, and the repository is now aligned to the Phase 2 workspace milestone.
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
  - `GET /api/v1/search`
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
  - Search three-bucket results
  - Today view for Review + Task
  - Upcoming view for Review + Task
  - Proposal list, generate, apply, and rollback
- Current implemented minimal governance details include:
  - Task supports `SYSTEM` and `USER`
  - Task Today supports optional `timezone_offset`
  - Review can derive `REVIEW_FOLLOW_UP` system tasks
  - duplicate open user task creation is rejected with `409 OPEN_TASK_ALREADY_EXISTS`
  - Proposal currently supports only `INTERPRETATION + LOW` via `REFRESH_INTERPRETATION`

## Known Gaps

- URL extraction is still a Phase 1 placeholder implementation and is not a full fetch/extraction pipeline.
- Proposal governance is still limited to `INTERPRETATION + LOW`; `METADATA`, `RELATION`, and higher-risk governance are not implemented.
- There is no complete account system; the current web flow uses explicit `user_id`.
- Formal Idea lifecycle, Trend Inbox, full PWA/offline support, and mobile apps are still out of scope.
- External search provider integration is still stubbed; `external_supplements` is not backed by a real provider yet.
- Full browser end-to-end regression is not yet complete; current verification is based on targeted backend tests, manual issue reproduction, and frontend build.
