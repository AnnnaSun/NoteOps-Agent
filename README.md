# NoteOps Agent

Phase 3 monorepo for the NoteOps Idea Lifecycle / Idea Workspace milestone. The current repository state has completed the minimal Phase 3 create / assess / task / query / web loops, and the current doc baseline has been synced through `Step 3.6` in `docs/codex/Plan.md`.

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

Local development defaults to the `local` profile and uses `OLLAMA`.

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Server runs on `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/api/v1/health
```

Production-like startup example:

```bash
cd server
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

## AI Configuration

The server keeps protocol-level providers only:

- `OPENAI_COMPATIBLE`
- `OLLAMA`

Current profile convention:

- `local`: defaults to `OLLAMA`
- `prod`: defaults to `OPENAI_COMPATIBLE`

`OPENAI_COMPATIBLE` is configured through an endpoint registry. Each route can choose:

- `endpoint`
- `model`

Minimal local default:

```yaml
noteops:
  ai:
    default-provider: OLLAMA
    ollama:
      base-url: http://localhost:11434
      model: deepseek-r1:8b
```

Example OpenAI-compatible endpoint registry for `kimi / deepseek / gemini / openrouter`:

```yaml
noteops:
  ai:
    default-provider: OPENAI_COMPATIBLE
    request-timeout: PT60S
    routes:
      capture-analysis:
        endpoint: kimi
        model: kimi-k2
      search-enhancement:
        endpoint: deepseek
        model: deepseek-r1
      note-summary:
        endpoint: gemini
        model: gemini-2.5-flash
      idea-assess:
        endpoint: openrouter
        model: anthropic/claude-3.7-sonnet
    openai-compatible:
      default-endpoint: kimi
      endpoints:
        kimi:
          base-url: https://api.moonshot.cn/v1
          api-key: ${KIMI_API_KEY}
          model: kimi-k2
        deepseek:
          base-url: https://api.deepseek.com/v1
          api-key: ${DEEPSEEK_API_KEY}
          model: deepseek-chat
        gemini:
          base-url: https://generativelanguage.googleapis.com/v1beta/openai
          api-key: ${GEMINI_API_KEY}
          model: gemini-2.5-flash
        openrouter:
          base-url: https://openrouter.ai/api/v1
          api-key: ${OPENROUTER_API_KEY}
          model: anthropic/claude-3.7-sonnet
```

Rules:

- Use `route.provider: OLLAMA` only when a route must explicitly go through Ollama native API.
- Keep non-compatible APIs out of `OPENAI_COMPATIBLE`.
- If an upstream is not OpenAI-compatible, normalize it in your gateway first or add a new protocol provider.

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

- Phase 1 minimal kernel and the Phase 2 workspace baseline are complete, and the repository is now aligned to the minimal Phase 3 Idea Workspace milestone.
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
  - `POST /api/v1/ideas`
  - `GET /api/v1/ideas`
  - `GET /api/v1/ideas/{id}`
  - `POST /api/v1/ideas/{id}/assess`
  - `POST /api/v1/ideas/{id}/generate-task`
- Current web workspace supports:
  - explicit `user_id` selection
  - `TEXT / URL` Capture submission
  - Note list and Note detail browsing
  - Search three-bucket results
  - Today view for Review + Task
  - Upcoming view for Review + Task
  - Proposal list, generate, apply, and rollback
  - Idea List and Idea Detail in the single-page workspace
  - Idea assessment result display
  - Idea task generation and related-task viewing
  - source Note jump from `FROM_NOTE` ideas
- Current implemented minimal governance details include:
  - Task supports `SYSTEM` and `USER`
  - Task supports `related_entity_type = NOTE / IDEA / REVIEW / NONE`
  - Idea source mode currently uses `FROM_NOTE / MANUAL`
  - Task Today supports optional `timezone_offset`
  - Review can derive `REVIEW_FOLLOW_UP` system tasks
  - duplicate open user task creation is rejected with `409 OPEN_TASK_ALREADY_EXISTS`
  - Proposal currently supports only `INTERPRETATION + LOW` via `REFRESH_INTERPRETATION`
  - Idea supports `CAPTURED -> ASSESSED -> PLANNED` in the currently implemented main path
  - Idea assess / task generation writes trace / event / structured logging

## Known Gaps

- URL extraction is still a Phase 1 placeholder implementation and is not a full fetch/extraction pipeline.
- Proposal governance is still limited to `INTERPRETATION + LOW`; `METADATA`, `RELATION`, and higher-risk governance are not implemented.
- There is no complete account system; the current web flow uses explicit `user_id`.
- Phase 3 is not fully closed yet: `Promote to Plan / Archive / Reopen` and the corresponding backend lifecycle commands are still missing.
- Idea Create still has backend/API support only; there is no dedicated web create form yet.
- Trend Inbox, full PWA/offline support, and mobile apps are still out of scope for the current milestone.
- External search provider integration is still stubbed; `external_supplements` is not backed by a real provider yet.
- Idea assess currently uses a stubbed idea agent contract, not a real external research provider.
- Full browser end-to-end regression is not yet complete; current verification is based on targeted backend tests, manual issue reproduction, and frontend build.
