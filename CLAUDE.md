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

Spring Boot 3.4.4 with Kotlin 2.1.10, Gradle Kotlin DSL, Java 21 toolchain. Deployed on Render.com via Docker.

```
com.strata.ftw/
├── FtwApplication.kt          # @SpringBootApplication + @EnableScheduling + @EnableAsync
├── config/
│   ├── SecurityConfig.kt      # Spring Security: JWT filter, CORS, security headers
│   ├── WebSocketConfig.kt     # STOMP broker at /ws, SockJS, JWT on CONNECT
│   ├── JacksonConfig.kt       # snake_case JSON, ISO-8601 dates
│   └── AsyncConfig.kt         # Thread pool for @Async tasks
├── domain/
│   ├── entity/                # 27 JPA entities (match existing Postgres schema)
│   └── repository/            # 27 Spring Data JPA repositories (single Repositories.kt file)
├── service/
│   ├── MarketplaceService.kt  # Core business logic (1:1 port from Elixir marketplace.ex)
│   ├── AuthService.kt         # JWT sign/verify (HS256), Argon2 passwords, role switching, TokenClaims data class
│   ├── FairTrustService.kt    # Verification pipeline + quality scoring
│   ├── EmailService.kt        # @Async email via Spring Mail
│   ├── PushService.kt         # @Async Expo push notifications
│   ├── StorageService.kt      # S3/R2 uploads with local fallback
│   └── FairRecordPdfService.kt# HTML certificate generation
├── ai/
│   └── AiGateway.kt           # FairPrice (ConcurrentHashMap from DB), FairScope (Caffeine 7d TTL), EstimateAgent (RunPod)
├── web/
│   ├── controller/            # 19 REST controllers
│   ├── filter/
│   │   ├── JwtAuthFilter.kt   # Bearer token -> SecurityContext
│   │   └── RateLimitFilter.kt # Bucket4j per-IP rate limiting
│   └── websocket/
│       └── MessageHandlers.kt # STOMP message handlers (jobs, bids, chat, typing, sub-jobs)
└── worker/
    └── ScheduledWorkers.kt    # 5 @Scheduled jobs (FairPrice refresh, FairScope cleanup, etc.)
```

---

## Database

PostgreSQL via JPA/Hibernate. Schema managed by Flyway (baseline-on-migrate, baseline-version=0). Hibernate ddl-auto=none.

28 entities matching the existing Postgres schema. UUID primary keys. Timestamps as `inserted_at`/`updated_at`.

### Entities (28)

Bid, Client, ContentFlag, Conversation, Dispute, DisputeEvidence, Estimate, FairPriceEntry, FairRecord, Invoice, Job, LineItem, Message, Notification, Project, PushToken, QbCredential, RevenueSnapshot, Review, SubBid, SubContractor, SubJob, SubPayout, TransactionLog, Upload, User, UserSetting, Verification

### Flyway Migrations

- `V1__baseline.sql` -- initial schema
- `V2__sub_contractor.sql` -- sub-contractor tables
- `V3__quickbooks_credentials.sql` -- QB OAuth credentials + invoice sync columns

---

## Auth

JWT (HS256) via `SECRET_KEY_BASE`. 24-hour TTL (86400s). Issuer: `ftw-realtime`, Audience: `ftw`.
Claims: user_id, email, role, roles (multi-role support).
Argon2 password hashing (compatible with Elixir argon2_elixir via Spring Security Argon2PasswordEncoder).

- `JwtAuthFilter` -- reads `Authorization: Bearer <token>`, sets Spring SecurityContext
- `WebSocketConfig` -- reads `token` header (or `Authorization` header) on STOMP CONNECT
- `AuthService.switchRole()` -- users can switch between their assigned roles
- `AuthService.activateSubContractorRole()` -- adds sub_contractor role to existing user

---

## REST API

19 controllers under `/api`:

| Controller   | Endpoints                                                                                                                                                                   |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Health       | `GET /api/health`                                                                                                                                                           |
| Auth         | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me`, `POST /api/auth/switch-role`                                                                         |
| Job          | `GET/POST /api/jobs`, `GET /api/jobs/{id}`, `POST /api/jobs/{id}/bids`, `POST /api/jobs/{id}/bids/{bidId}/accept`, `POST /api/jobs/{id}/transition`                         |
| SubJob       | `GET/POST /api/sub-jobs`, `GET /api/sub-jobs/{id}`, `GET /api/sub-jobs/my-posts`, `POST /api/sub-jobs/{subJobId}/bids`, `POST /api/sub-jobs/{subJobId}/bids/{bidId}/accept` |
| Chat         | `GET/POST /api/chat/{conversationId}`                                                                                                                                       |
| Estimate     | `GET/POST /api/estimates`, `GET/PATCH/DELETE /api/estimates/{id}`                                                                                                           |
| Invoice      | `GET/POST /api/invoices`, `GET/PATCH /api/invoices/{id}`                                                                                                                    |
| Project      | `GET/POST /api/projects`, `GET/PATCH /api/projects/{id}`, `GET /api/projects/{id}/record`                                                                                   |
| Client       | `GET/POST /api/clients`, `GET/PATCH/DELETE /api/clients/{id}`                                                                                                               |
| Review       | `GET/POST /api/reviews`, `GET /api/reviews/{id}`, `POST /api/reviews/{id}/respond`                                                                                          |
| AI           | `GET /api/ai/fair-price`, `GET /api/ai/stats`, `POST /api/ai/estimate`, `POST /api/ai/fair-scope`                                                                           |
| FairRecord   | `GET /api/records/{publicId}`, `GET /api/records/{publicId}/certificate`, `GET /api/contractors/{id}/records`, `POST /api/records/{id}/confirm`                             |
| Verification | `GET /api/contractor/verification`, `POST /api/contractor/verification/{step}`                                                                                              |
| Webhook      | `POST /api/webhooks/persona`, `POST /api/webhooks/checkr`                                                                                                                   |
| Push         | `POST /api/push/register`, `DELETE /api/push/unregister`                                                                                                                    |
| Notification | `GET /api/notifications`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`                                                                           |
| Upload       | `POST/GET /api/uploads`, `DELETE /api/uploads/{id}`                                                                                                                         |
| QuickBooks   | `GET /api/quickbooks/callback`, `GET /api/quickbooks/status`, `DELETE /api/quickbooks/disconnect`, `POST /api/quickbooks/invoices/{id}/sync`, `POST /api/quickbooks/invoices/{id}/payment`, `GET /api/quickbooks/invoices/{id}` |
| Settings     | `GET/PUT /api/settings`                                                                                                                                                     |

### Public Endpoints (no auth required)

- `GET /api/health`
- `POST /api/auth/login`, `POST /api/auth/register`
- `GET /api/jobs`, `GET /api/jobs/*`
- `GET /api/sub-jobs`, `GET /api/sub-jobs/*`
- `GET /api/ai/fair-price`, `GET /api/ai/stats`
- `GET /api/records/*`, `GET /api/records/*/certificate`
- `GET /api/quickbooks/callback` (OAuth redirect from Intuit)
- `POST /api/webhooks/**`
- `/ws/**`

---

## WebSocket (STOMP)

Endpoint: `/ws` (SockJS). JWT auth on CONNECT via `token` header or `Authorization: Bearer` header.

### Topics (subscribe)

| Topic                  | Events                       |
| ---------------------- | ---------------------------- |
| `/topic/jobs.feed`     | `job:posted`, `job:updated`  |
| `/topic/job.{id}`      | `bid:placed`, `bid:accepted` |
| `/topic/chat.{id}`     | `message:new`, `typing`      |
| `/topic/user.{id}`     | `notification`               |
| `/topic/sub-jobs.feed` | `sub_job:posted`             |
| `/topic/sub-job.{id}`  | `sub_bid:placed`             |

### Send Destinations

| Destination               | Purpose                  |
| ------------------------- | ------------------------ |
| `/app/jobs.feed.post`     | Post a new job           |
| `/app/job.{id}.bid`       | Place a bid on a job     |
| `/app/job.{id}.accept`    | Accept a bid             |
| `/app/chat.{id}.send`     | Send a chat message      |
| `/app/chat.{id}.typing`   | Typing indicator         |
| `/app/sub-jobs.feed.post` | Post a new sub-job       |
| `/app/sub-job.{id}.bid`   | Place a bid on a sub-job |

---

## Background Jobs

| Worker                  | Schedule           | Purpose                                     |
| ----------------------- | ------------------ | ------------------------------------------- |
| refreshFairPrices       | Sunday 3am UTC     | Reload FairPrice cache from DB              |
| cleanupFairScope        | Daily 4am UTC      | Expire old FairScope Caffeine cache entries |
| checkVerificationExpiry | Daily 5am UTC      | Mark expired verifications                  |
| recomputeQualityScores  | Sunday 6am UTC     | Recompute contractor quality scores         |
| captureRevenueSnapshot  | Daily midnight UTC | Daily revenue aggregation                   |

---

## Environment Variables

| Variable                 | Notes                                                          |
| ------------------------ | -------------------------------------------------------------- |
| `DB_HOST`                | PostgreSQL host (default: localhost)                           |
| `DB_PORT`                | PostgreSQL port (default: 5432)                                |
| `DB_NAME`                | PostgreSQL database name (default: ftw_realtime_dev)           |
| `DB_USERNAME`            | PostgreSQL user (default: postgres)                            |
| `DB_PASSWORD`            | PostgreSQL password (default: postgres)                        |
| `SECRET_KEY_BASE`        | JWT signing key (default: dev-secret-key-change-in-production) |
| `PORT`                   | HTTP port (default: 4000, Render uses 10000)                   |
| `POOL_SIZE`              | Hikari DB pool size (default: 10)                              |
| `SPRING_PROFILES_ACTIVE` | Spring profile (set to `prod` on Render)                       |
| `SMTP_HOST`              | SMTP relay host (default: localhost)                           |
| `SMTP_PORT`              | SMTP relay port (default: 587)                                 |
| `SMTP_USERNAME`          | SMTP username                                                  |
| `SMTP_PASSWORD`          | SMTP password                                                  |
| `EXPO_ACCESS_TOKEN`      | Expo push notifications                                        |
| `STORAGE_BUCKET`         | S3/R2 bucket name                                              |
| `STORAGE_ENDPOINT`       | S3-compatible endpoint URL                                     |
| `STORAGE_LOCAL_PATH`     | Local fallback upload dir (default: uploads)                   |
| `AWS_REGION`             | S3 region (default: us-east-1)                                 |
| `AWS_ACCESS_KEY_ID`      | S3 access key                                                  |
| `AWS_SECRET_ACCESS_KEY`  | S3 secret key                                                  |
| `RUNPOD_URL`             | ConstructionAI inference endpoint (RunPod Serverless)          |
| `QB_CLIENT_ID`           | QuickBooks OAuth2 client ID (Intuit developer portal)          |
| `QB_CLIENT_SECRET`       | QuickBooks OAuth2 client secret                                |
| `QB_REDIRECT_URI`        | OAuth callback URL (default: http://localhost:4000/api/quickbooks/callback) |
| `QB_ENVIRONMENT`         | `sandbox` or `production` (default: sandbox)                   |
| `QB_BASE_URL`            | QB API base (default: sandbox-quickbooks.api.intuit.com)       |
| `ADMIN_PASSWORD`         | Admin auth (default: faircommand)                              |

---

## Deployment

Docker on Render.com. `render.yaml` provisions web service (starter plan, Oregon) + PostgreSQL (starter plan, Oregon).

Dockerfile: multi-stage build using `eclipse-temurin:21-jdk-alpine` (build) and `eclipse-temurin:21-jre-alpine` (runtime).

---

## Key Constraints

- API paths are identical to the Elixir version -- frontend works without changes (except WebSocket)
- Frontend WebSocket client uses `@stomp/stompjs` instead of `phoenix` npm package
- Do not modify the database schema -- Hibernate validates against existing tables, Flyway manages migrations
- CORS allows: localhost:3000, fairtradeworker.com, www.fairtradeworker.com, fairtradeworker.vercel.app, \*.vercel.app
- JSON output uses snake_case naming, null values excluded, ISO-8601 dates
- Max file upload size: 10MB
