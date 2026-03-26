# CLAUDE.md -- ftw-realtime

Phoenix WebSocket + REST backend for FairTradeWorker. Postgres via Ecto for persistence, PubSub for real-time broadcasts, Oban for background jobs, Joken for JWT auth.

---

## Running the project

```bash
mix setup          # deps, ecto.create, ecto.migrate, seeds, assets
mix phx.server     # start on localhost:4000
```

Reset database: `mix ecto.reset`

Before committing:

```bash
mix precommit
```

This runs `compile --warnings-as-errors`, `deps.unlock --unused`, `format`, and `test`. Fix everything it reports before pushing.

---

## Architecture

```
FtwRealtime.Application (supervisor)
  FtwRealtimeWeb.Telemetry
  FtwRealtime.Repo                   <-- Ecto Postgres
  DNSCluster
  Phoenix.PubSub (name: FtwRealtime.PubSub)
  FtwRealtimeWeb.Presence            <-- online status, typing indicators
  FtwRealtime.AI.FairPrice           <-- FairPrice cache GenServer
  FtwRealtime.AI.FairScope           <-- FairScope cache GenServer
  FtwRealtime.AI.CostTracker         <-- AI cost tracking GenServer
  Oban                               <-- background job processor
  FtwRealtimeWeb.Endpoint
```

---

## Database

Postgres with Ecto. 22 schemas, 16 migrations.

### Core marketplace schemas

| Schema | Module | Notes |
|---|---|---|
| `users` | `Marketplace.User` | Homeowners, contractors, admins. UUID PKs. Argon2 password hashing. |
| `jobs` | `Marketplace.Job` | Status enum: open, bidding, awarded, in_progress, completed, disputed, cancelled |
| `bids` | `Marketplace.Bid` | One bid per contractor per job (unique constraint) |
| `conversations` | `Marketplace.Conversation` | Unique per job+homeowner+contractor triple |
| `messages` | `Marketplace.Message` | Belongs to conversation + sender |

### Business schemas

| Schema | Module | Notes |
|---|---|---|
| `clients` | `Marketplace.Client` | Contractor's client records |
| `estimates` | `Marketplace.Estimate` | Estimate documents with line items |
| `line_items` | `Marketplace.LineItem` | Belongs to estimate |
| `invoices` | `Marketplace.Invoice` | Invoicing |
| `projects` | `Marketplace.Project` | Project tracking |
| `reviews` | `Marketplace.Review` | Post-job reviews |

### Platform schemas

| Schema | Module | Notes |
|---|---|---|
| `notifications` | `Marketplace.Notification` | User notifications |
| `uploads` | `Marketplace.Upload` | File uploads |
| `user_settings` | `Marketplace.UserSetting` | Per-user settings |
| `verifications` | `Marketplace.Verification` | Contractor identity/background verification |
| `fair_records` | `Marketplace.FairRecord` | Publicly verifiable work records |
| `push_tokens` | `Marketplace.PushToken` | Expo push notification tokens per user/device |
| `content_flags` | `Marketplace.ContentFlag` | Content moderation flags |
| `disputes` | `Marketplace.Dispute` | Job disputes |
| `dispute_evidences` | `Marketplace.DisputeEvidence` | Dispute supporting evidence |
| `revenue_snapshots` | `Marketplace.RevenueSnapshot` | Daily revenue tracking |
| `transaction_logs` | `Marketplace.TransactionLog` | Audit trail |

All schemas use `binary_id` primary keys. Migrations in `priv/repo/migrations/`.

---

## Auth system

JWT via Joken (HS256, signed with `SECRET_KEY_BASE`). Tokens contain `user_id`, `email`, `role`, `exp`. 24-hour TTL.

- `FtwRealtime.Auth` -- token generation and verification
- `FtwRealtimeWeb.Plugs.Auth` -- REST plug, reads `Authorization: Bearer <token>`, sets `current_user_id`/`current_email`/`current_role` on conn assigns. Returns 401 on failure.
- `FtwRealtimeWeb.UserSocket` -- WebSocket auth, verifies token from connect params, assigns `user_id`/`email`/`role`. Rejects anonymous connections.

---

## Channels (lib/ftw_realtime_web/channels/)

All channels connect through `FtwRealtimeWeb.UserSocket` at `/socket`. JWT token required.

| Module | Topic pattern | Purpose |
|---|---|---|
| `JobChannel` | `jobs:feed` | Live job board feed. Role check: only homeowners can post. |
| `BidChannel` | `job:<job_id>` | Per-job bidding. Role check: only contractors bid, only job-owner homeowners accept. |
| `ChatChannel` | `chat:<conversation_id>` | Messaging with typing indicators via Presence. Participant check on join. |
| `NotificationChannel` | `user:<user_id>` | User-specific notifications + online presence. User can only join own channel. |

### Channel rate limiting

`FtwRealtimeWeb.RateLimiter` -- per-connection rate limiting using the process dictionary. No shared state, no ETS, no bottleneck. Each channel process has its own counters.

---

## Plugs (lib/ftw_realtime_web/plugs/)

| Plug | Purpose |
|---|---|
| `Auth` | JWT verification for REST endpoints. Reads Bearer token, sets assigns, halts with 401 on failure. |
| `CORS` | Origin allowlist. Prod: 3 FTW origins. Dev: `*`. Handles OPTIONS preflight. |
| `RateLimit` | ETS-based per-IP+path rate limiting. Returns 429 with `Retry-After` header. Configurable limit/window. |
| `AIRateLimit` | Per-feature AI rate limiting (ETS). Keys on user_id or IP. Default limits: fair_price=60, fair_scope=10, estimate_agent=5 per minute. |
| `SecurityHeaders` | Sets X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, HSTS, CSP on all responses. Applied at the endpoint level. |
| `AdminAuth` | Session-based password auth for `/admin`. Reads `ADMIN_PASSWORD` env var (default: `"faircommand"`). |

---

## AI subsystem (lib/ftw_realtime/ai/)

| Module | Purpose |
|---|---|
| `AI` | Top-level AI context |
| `AI.Gateway` | HTTP client for RunPod/external AI services |
| `AI.EstimateAgent` | Construction estimation via RunPod (ConstructionAI) |
| `AI.FairPrice` | GenServer cache for fair market pricing data |
| `AI.FairPriceEntry` | Schema for FairPrice entries |
| `AI.FairScope` | GenServer cache for scope-of-work analysis |
| `AI.CostTracker` | GenServer tracking AI API costs |
| `AI.RunpodHealth` | RunPod endpoint health checking |

---

## Ops modules (lib/ftw_realtime/ops/)

| Module | Purpose |
|---|---|
| `Ops.FairTrust` | Trust scoring for contractors |
| `Ops.FairLedger` | Financial ledger operations |

---

## Utility modules (lib/ftw_realtime/)

| Module | Purpose |
|---|---|
| `Push` | Sends mobile push notifications via the Expo Push API. Looks up all push tokens for a user and sends to each device. |
| `Storage` | File storage abstraction. S3-compatible (R2, S3, MinIO) in production, local disk in dev. |
| `FairRecordPdf` | Generates printable HTML certificates for FairRecord verification. |
| `Emails` | Email composition (used by Swoosh/Mailer). |
| `Mailer` | Swoosh mailer module. |

---

## Oban workers (lib/ftw_realtime/workers/)

| Worker | Queue | Schedule |
|---|---|---|
| `EmailWorker` | default | On demand |
| `NotificationWorker` | notifications | On demand |
| `MatchingWorker` | matching | On demand |
| `FairPriceComputeWorker` | default | Weekly (Sunday 3am UTC) |
| `FairScopeCleanupWorker` | default | Daily (4am UTC) |
| `VerificationExpiryWorker` | default | Daily (5am UTC) |
| `QualityScoreWorker` | default | Weekly (Sunday 6am UTC) |
| `RevenueSnapshotWorker` | default | Daily (midnight UTC) |

Oban queues: `default` (10), `notifications` (5), `matching` (3), `sync` (3).

---

## Context (lib/ftw_realtime/marketplace.ex)

`FtwRealtime.Marketplace` is the single context for all marketplace operations. All functions use Ecto Repo for persistence and broadcast via PubSub on state changes.

| PubSub topic | Events broadcast |
|---|---|
| `"jobs"` | `{"job:posted", job}`, `{"job:updated", job}` |
| `"job:<job_id>"` | `{"bid:placed", bid}`, `{"bid:accepted", bid}` |
| `"chat:<conversation_id>"` | `{"message:new", message}` |

Channels and `MarketplaceLive` subscribe to these topics and push/assign accordingly.

---

## REST API (lib/ftw_realtime_web/controllers/api/)

19 controllers, all under `/api`:

| Controller | Endpoints |
|---|---|
| `HealthController` | `GET /api/health` -- no pipeline, no CORS |
| `AuthController` | Login, token validation |
| `UserController` | Registration, user lookup |
| `JobController` | Jobs CRUD, bid placement/acceptance, state transitions |
| `ChatController` | Message list + send |
| `AIController` | AI estimation |
| `FairPriceController` | FairPrice lookup + stats (public, AI rate limited) |
| `FairScopeController` | FairScope analysis |
| `EstimateController` | Estimate CRUD |
| `InvoiceController` | Invoice CRUD |
| `ProjectController` | Project CRUD |
| `ClientController` | Client CRUD |
| `PushController` | Push notification token registration/unregistration |
| `VerificationController` | Contractor verification + webhooks (Persona, Checkr) |
| `FairRecordController` | Public verification, contractor/project records, printable certificates |
| `ReviewController` | Review CRUD + responses |
| `NotificationController` | Notification list + mark read |
| `UploadController` | File upload/list/delete |
| `SettingsController` | User settings |

The `:api` pipeline applies `FtwRealtimeWeb.Plugs.CORS`. In production, CORS allows only the three FTW origins. In dev, it allows `*`.

---

## LiveView (lib/ftw_realtime_web/live/)

| Route | Module | Purpose |
|---|---|---|
| `/marketplace` | `MarketplaceLive` | Internal test/monitor view. Subscribes to PubSub directly. |
| `/admin` | `AdminLive` | FairCommand dashboard. AI costs, trust scores, ledger, system health. Auto-refreshes every 15s. Password protected. |

Neither is part of the FTW frontend integration.

---

## Adding new features

**New real-time event:**
1. Add business logic to `FtwRealtime.Marketplace` -- Repo query, `broadcast/3` to the right PubSub topic.
2. Add a `handle_info` clause in the relevant channel to push the event to clients.
3. If `MarketplaceLive` or `AdminLive` needs to react, add a `handle_info` there too.

**New schema:**
1. Create schema in `lib/ftw_realtime/marketplace/`.
2. Create migration: `mix ecto.gen.migration create_your_table`.
3. Add context functions in `marketplace.ex`.

**New channel:**
1. Create `lib/ftw_realtime_web/channels/your_channel.ex`, `use Phoenix.Channel`.
2. Register it in `UserSocket`: `channel "your:*", FtwRealtimeWeb.YourChannel`.

**New REST endpoint:**
1. Add the controller in `lib/ftw_realtime_web/controllers/api/`.
2. Add the route in `router.ex` under the appropriate scope/pipeline.
3. Auth-required endpoints go in the `:authenticated` scope. Public endpoints go in the `:api` scope.

**New background job:**
1. Create worker in `lib/ftw_realtime/workers/`, `use Oban.Worker`.
2. For scheduled jobs, add crontab entry in `config/config.exs` under `Oban.Plugins.Cron`.
3. For on-demand jobs, enqueue with `Oban.insert/1` from context functions.

**New AI feature:**
1. Add module in `lib/ftw_realtime/ai/`.
2. If it needs a cache GenServer, add to Application supervisor children (before Oban and Endpoint).
3. Add rate limit defaults in `FtwRealtimeWeb.Plugs.AIRateLimit`.

---

## Deployment

Hosted on Render.com via Docker. `render.yaml` provisions:
- `ftw-realtime` web service (Docker, starter plan, Oregon)
- `ftw-realtime-db` Postgres database (starter plan, Oregon)

The Dockerfile runs migrations on startup via `FtwRealtime.Release.migrate/0`.

---

## Environment variables

| Variable | Notes |
|---|---|
| `DATABASE_URL` | Required in prod. Provided by Render Postgres. |
| `SECRET_KEY_BASE` | Required in prod. `mix phx.gen.secret` to generate. Also used as JWT signing key. |
| `PHX_HOST` | Required in prod. Sets the endpoint URL host. |
| `PHX_SERVER` | Set `"true"` to start the server on release boot. |
| `PORT` | HTTP port. Defaults to `4000`. Render uses `10000`. |
| `POOL_SIZE` | DB connection pool. Defaults to `10`. |
| `ECTO_IPV6` | Set `"true"` for IPv6 database connections. |
| `DNS_CLUSTER_QUERY` | Optional. Node clustering via DNS. Leave unset for single-node. |
| `ADMIN_PASSWORD` | Optional. Password for `/admin` dashboard. Defaults to `"faircommand"`. |
| `EXPO_ACCESS_TOKEN` | Optional. Expo Push API access token for mobile push notifications. |
| `STORAGE_BUCKET` | Optional. S3-compatible bucket for file uploads. Falls back to local disk if unset. |
| `AWS_ACCESS_KEY_ID` | Optional. S3/R2 access key for file storage. |
| `AWS_SECRET_ACCESS_KEY` | Optional. S3/R2 secret key for file storage. |
| `AWS_REGION` | Optional. S3 region. Defaults to `us-east-1`. |
| `STORAGE_ENDPOINT` | Optional. Custom S3 endpoint for R2/MinIO. |
| `SMTP_HOST` | Optional. SMTP relay for outbound email (Swoosh). Falls back to local adapter. |
| `SMTP_PORT` | Optional. SMTP port. Defaults to `587`. |
| `SMTP_USERNAME` | Optional. SMTP auth username. |
| `SMTP_PASSWORD` | Optional. SMTP auth password. |

---

## Key constraints

- Do not add LiveView routes for end-user features -- this service is a WebSocket/API backend. The FTW frontend (Next.js) consumes it.
- Do not disable `mix precommit` checks. Warnings-as-errors is enforced.
- CORS origins are compile-time constants in `FtwRealtimeWeb.Plugs.CORS`. If you add a new FTW deploy URL, update that list and the `check_origin` list in `endpoint.ex`.
- SecurityHeaders plug is applied at the endpoint level (not per-pipeline). Changes affect all routes.
- Channel rate limiting uses process dictionary (per-connection). REST rate limiting uses ETS (per-IP+path). AI rate limiting uses a separate ETS table (per-user+feature or per-IP+feature).
- JWT tokens are signed with `SECRET_KEY_BASE`. Changing the secret invalidates all active tokens.
