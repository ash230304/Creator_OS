# CreatorOS

> AI-powered content repurposing platform — from idea to captioned short-form clips in one pipeline.

[![CI](https://github.com/YOUR_USERNAME/creatoros/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/creatoros/actions)

---

## What it does

1. You enter a **content idea**
2. AI generates a **structured video script** (hook, dialogue, B-roll, editing instructions, CTA)
3. You **upload raw video footage**
4. CreatorOS runs an async pipeline:
   - Transcribes with **Whisper** (speech → timestamped text)
   - Detects highlight segments (heuristic or LLM scoring)
   - Cuts clips using **FFmpeg**
   - Burns captions into each clip
5. You get **short-form clips ready to post** — from the dashboard

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL 16 + Flyway migrations |
| Auth | JWT (Spring Security, stateless) |
| AI / Scripts | Provider-agnostic (OpenAI / Groq — switchable) |
| Transcription | OpenAI Whisper |
| Video processing | FFmpeg |
| Storage | Local filesystem (S3-ready abstraction) |
| Async jobs | Spring `@Async` with thread-pool executor |
| Deployment | Railway / Render / Fly.io |
| CI/CD | GitHub Actions |
| Container | Docker (multi-stage build) |

---

## Architecture

```
Creator
  │
  ▼ REST / JSON
Frontend (React — Week 7)
  │
  ▼ REST / JSON
Spring Boot API
  ├── auth · user · project
  ├── script (AI generation)
  ├── video (upload + storage)
  ├── transcription (Whisper)
  ├── clipping (FFmpeg)
  └── processing (async job runner)
  │
  ├── PostgreSQL
  ├── File Storage (local / S3)
  └── External APIs: LLM · Whisper
```

> **Principle:** The frontend never talks directly to PostgreSQL, FFmpeg, Whisper, or any LLM.
> Everything goes through the Spring Boot API.

---

## Running Locally

### Prerequisites
- Java 17
- Maven
- PostgreSQL running on port 5432

### Setup

```bash
# 1. Create database
createdb creatoros

# 2. Start backend
cd backend/
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/creatoros \
SPRING_DATASOURCE_USERNAME=your_pg_username \
SPRING_DATASOURCE_PASSWORD="" \
SPRING_FLYWAY_ENABLED=false \
mvn spring-boot:run

# 3. Verify
curl http://localhost:8080/api/v1/health
# → {"success":true,"data":{"status":"UP","service":"creatoros-backend"}}
```

### With Docker Compose

```bash
docker compose up
```

---

## API Overview

All routes prefixed with `/api/v1/`

| Method | Route | Auth | Description |
|---|---|---|---|
| GET | `/health` | Public | Health check |
| POST | `/auth/register` | Public | Register user |
| POST | `/auth/login` | Public | Login, get JWT |
| GET | `/users/me` | JWT | Get own profile |
| POST | `/projects` | JWT | Create project |
| GET | `/projects` | JWT | List projects |
| POST | `/scripts/generate` | JWT | Generate AI script |
| POST | `/videos` | JWT | Upload video |
| POST | `/videos/{id}/process` | JWT | Start processing |
| GET | `/jobs/{jobId}` | JWT | Poll job status |
| GET | `/videos/{id}/transcript` | JWT | Get transcript |
| GET | `/videos/{id}/clips` | JWT | Get clips |

---

## Project Status

| Week | Feature | Status |
|---|---|---|
| 1 | Foundation, docs, Docker, CI | ✅ Done |
| 2 | DB schema, JWT auth, projects CRUD | 🔵 Next |
| 3 | AI script generation | ⬜ Upcoming |
| 4 | Video upload, transcription | ⬜ Upcoming |
| 5 | Clip detection + FFmpeg | ⬜ Upcoming |
| 6 | Auto captions, output polish | ⬜ Upcoming |
| 7 | Dashboard + deployment | ⬜ Upcoming |
| 8 | Demo + case study | ⬜ Upcoming |

---

## V2 Backlog

Designed to extend to — deliberately deferred to keep v1 shippable:

- Kafka-based event streaming pipeline
- ML-assisted virality prediction
- Multi-platform export (TikTok / Instagram API)
- Billing and team collaboration
- Redis caching layer

---

## Developer Docs

See [`backend/README.md`](backend/README.md) for a full learning-first breakdown of every file, pattern, and tech decision.
