# NoteOps Agent

Phase 1 bootstrap monorepo for the NoteOps Agent project.

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

## Current Scope

- This repository is only initialized for Phase 1 bootstrap.
- The next implementation step is database migration setup.
- Search, Idea lifecycle, Trend Inbox, and PWA/offline features are not implemented.
