# CLAUDE.md -- ftw-realtime

Spring Boot 3.4 (Kotlin) WebSocket + REST backend for FairTradeWorker. PostgreSQL via JPA/Hibernate, STOMP for real-time, @Scheduled for background jobs, JWT for auth.

---

## Quick Commands

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew bootRun              # start on localhost:4000
./gradlew compileKotlin        # compile check
./gradlew build                # compile + test
./gradlew bootJar              # build fat jar
docker build -t ftw-realtime . # docker image
```

---

## Architecture

Spring Boot 3.4 with Kotlin, Gradle Kotlin DSL. Deployed on Render.com via Docker.

```
com.strata.ftw/
├── FtwApplication.kt          # @SpringBootApplication + @EnableScheduling + @EnableAsync
├── config/
│   ├── SecurityConfig.kt      # Spring Security: JWT filter, CORS, security headers
│   ├── WebSocketConfig.kt     # STOMP broker at /ws, SockJS, JWT on CONNECT
│   ├── JacksonConfig.kt       # snake_case JSON, ISO-8601 dates
│   └── AsyncConfig.kt         # Thread pool for @Async tasks
├── domain/
│   ├── entity/                # 23 JPA entities (match existing Postgres schema)
│   └── repository/            # 23 Spring Data JPA repositories
├── service/
│   ├── MarketplaceService.kt  # Core business logic (1:1 port from Elixir marketplace.ex)
│   ├── AuthService.kt         # JWT sign/verify (HS256), Argon2 passwords
│   ├── FairTrustService.kt    # Verification pipeline + quality scoring
│   ├── EmailService.kt        # @Async email via Spring Mail
│   ├── PushService.kt         # @Async Expo push notifications
│   ├── StorageService.kt      # S3/R2 uploads with local fallback
│   └── FairRecordPdfService.kt# HTML certificate generation
├── ai/
│   └── AiGateway.kt           # FairPrice (Caffeine cache from DB), FairScope (7d TTL), EstimateAgent (RunPod)
├── web/
│   ├── controller/            # 17 REST controllers (identical /api/* paths)
│   ├── filter/
│   │   ├── JwtAuthFilter.kt   # Bearer token → SecurityContext
│   │   └── RateLimitFilter.kt # Bucket4j per-IP rate limiting
│   └── websocket/
│       └── MessageHandlers.kt # STOMP message handlers (jobs, bids, chat, typing)
└── worker/
    └── ScheduledWorkers.kt    # 5 @Scheduled jobs (FairPrice refresh, FairScope cleanup, etc.)
```

---

## Database

PostgreSQL via JPA/Hibernate. Schema managed by Flyway (baseline-on-migrate). Hibernate ddl-auto=none.

23 entities matching the existing Ecto schema exactly. UUID primary keys. Timestamps as `inserted_at`/`updated_at`.

---

## Auth

JWT (HS256) via `SECRET_KEY_BASE`. 24-hour TTL. Claims: user_id, email, role.
Argon2 password hashing (compatible with Elixir argon2_elixir).

- `JwtAuthFilter` — reads `Authorization: Bearer <token>`, sets Spring SecurityContext
- `WebSocketConfig` — reads `token` header on STOMP CONNECT

---

## REST API

17 controllers under `/api`, identical paths to the Elixir version:

| Controller | Endpoints |
|---|---|
| Health | `GET /api/health` |
| Auth | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me` |
| Job | `GET/POST /api/jobs`, `GET /api/jobs/{id}`, `POST /api/jobs/{id}/bids`, `POST /api/jobs/{id}/bids/{bidId}/accept`, `POST /api/jobs/{id}/transition` |
| Chat | `GET/POST /api/chat/{conversationId}` |
| Estimate | CRUD `/api/estimates` |
| Invoice | CRUD `/api/invoices` |
| Project | CRUD `/api/projects`, `GET /api/projects/{id}/record` |
| Client | CRUD `/api/clients` |
| Review | CRUD `/api/reviews`, `POST /api/reviews/{id}/respond` |
| AI | `GET /api/ai/fair-price`, `GET /api/ai/stats`, `POST /api/ai/estimate`, `POST /api/ai/fair-scope` |
| FairRecord | `GET /api/records/{publicId}`, `GET /api/records/{publicId}/certificate`, `GET /api/contractors/{id}/records`, `POST /api/records/{id}/confirm` |
| Verification | `GET/POST /api/contractor/verification/{step}` |
| Webhook | `POST /api/webhooks/persona`, `POST /api/webhooks/checkr` |
| Push | `POST /api/push/register`, `DELETE /api/push/unregister` |
| Notification | `GET /api/notifications`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` |
| Upload | `POST/GET/DELETE /api/uploads` |
| Settings | `GET/PUT /api/settings` |

---

## WebSocket (STOMP)

Endpoint: `/ws` (SockJS). JWT auth on CONNECT via `token` header.

| Topic | Events |
|---|---|
| `/topic/jobs.feed` | `job:posted`, `job:updated` |
| `/topic/job.{id}` | `bid:placed`, `bid:accepted` |
| `/topic/chat.{id}` | `message:new`, `typing` |
| `/topic/user.{id}` | `notification` |

Send actions via `/app/jobs.feed.post`, `/app/job.{id}.bid`, `/app/chat.{id}.send`, `/app/chat.{id}.typing`.

---

## Background Jobs

| Worker | Schedule | Purpose |
|---|---|---|
| refreshFairPrices | Sunday 3am UTC | Reload FairPrice cache from DB |
| cleanupFairScope | Daily 4am UTC | Expire old FairScope entries |
| checkVerificationExpiry | Daily 5am UTC | Mark expired verifications |
| recomputeQualityScores | Sunday 6am UTC | Recompute contractor scores |
| captureRevenueSnapshot | Daily midnight UTC | Daily revenue aggregation |

---

## Environment Variables

| Variable | Notes |
|---|---|
| `DB_HOST` | PostgreSQL host (default: localhost) |
| `DB_PORT` | PostgreSQL port (default: 5432) |
| `DB_NAME` | PostgreSQL database name (default: ftw_realtime_dev) |
| `DB_USERNAME` | PostgreSQL user (default: postgres) |
| `DB_PASSWORD` | PostgreSQL password (default: postgres) |
| `SECRET_KEY_BASE` | JWT signing key (same as Elixir) |
| `PORT` | HTTP port (default: 4000, Render uses 10000) |
| `POOL_SIZE` | Hikari DB pool size (default: 10) |
| `SPRING_PROFILES_ACTIVE` | Spring profile (set to `prod` on Render) |
| `SMTP_HOST` | SMTP relay host (default: localhost) |
| `SMTP_PORT` | SMTP relay port (default: 587) |
| `SMTP_USERNAME` | SMTP username |
| `SMTP_PASSWORD` | SMTP password |
| `EXPO_ACCESS_TOKEN` | Expo push notifications |
| `STORAGE_BUCKET` | S3/R2 bucket name |
| `STORAGE_ENDPOINT` | S3-compatible endpoint URL |
| `STORAGE_LOCAL_PATH` | Local fallback upload dir (default: uploads) |
| `AWS_REGION` | S3 region (default: us-east-1) |
| `AWS_ACCESS_KEY_ID` | S3 access key |
| `AWS_SECRET_ACCESS_KEY` | S3 secret key |
| `RUNPOD_URL` | AI inference endpoint |
| `ADMIN_PASSWORD` | Admin auth (default: faircommand) |

---

## Deployment

Docker on Render.com. `render.yaml` provisions web service + Postgres.

---

## Key Constraints

- API paths are identical to the Elixir version — frontend works without changes (except WebSocket)
- Frontend WebSocket client uses `@stomp/stompjs` instead of `phoenix` npm package
- Do not modify the database schema — Hibernate validates against existing tables
- CORS allows: localhost:3000, fairtradeworker.com, www.fairtradeworker.com, fairtradeworker.vercel.app, *.vercel.app
