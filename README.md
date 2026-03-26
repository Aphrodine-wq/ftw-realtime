# ftw-realtime

Phoenix WebSocket + REST backend for FairTradeWorker. Handles live bidding, real-time job feed updates, contractor/homeowner chat, AI estimation, invoicing, and contractor verification. Deployed to Render.com via Docker.

- **Stack:** Elixir 1.15+, Phoenix 1.8, Bandit HTTP server, Postgres via Ecto, Oban background jobs
- **State:** Postgres database (Ecto) + PubSub for real-time broadcasts
- **Transport:** Phoenix Channels over WebSocket + Phoenix LiveView for internal dashboards
- **Auth:** JWT (Joken, HS256) for API and WebSocket. Argon2 password hashing.
- **Deployed at:** `https://ftw-realtime.onrender.com`

---

## Local development

```bash
mix setup
```

This runs `deps.get`, `ecto.create`, `ecto.migrate`, seeds, and asset builds.

```bash
mix phx.server
```

Or inside IEx:

```bash
iex -S mix phx.server
```

The server starts on `http://localhost:4000`. The live marketplace dashboard is at `http://localhost:4000/marketplace`. The admin dashboard is at `http://localhost:4000/admin` (password protected).

Reset database:

```bash
mix ecto.reset
```

---

## WebSocket channels

Connect to `/socket` via Phoenix Socket. Pass a JWT `token` as a connect param. The socket verifies the token and assigns `user_id`, `email`, and `role`. Anonymous connections are rejected.

### `jobs:feed` -- JobChannel

Subscribe to the live job board.

| Event (server -> client) | Payload |
|---|---|
| `jobs:list` | `%{jobs: [...]}` -- sent on join |
| `job:posted` | job map |
| `job:updated` | updated job map (e.g. after a bid is placed or accepted) |

| Event (client -> server) | Payload |
|---|---|
| `post_job` | `%{title, description, category, location, budget_min, budget_max}` -- homeowner role required |

### `job:<job_id>` -- BidChannel

Subscribe to a specific job for live bid activity.

| Event (server -> client) | Payload |
|---|---|
| `job:details` | `%{job: ..., bids: [...]}` -- sent on join |
| `bid:placed` | new bid map |
| `bid:accepted` | accepted bid map |

| Event (client -> server) | Payload |
|---|---|
| `place_bid` | `%{amount, message, timeline}` -- contractor role required |
| `accept_bid` | `%{bid_id: "..."}` -- homeowner role required, must be job owner |

### `chat:<conversation_id>` -- ChatChannel

Real-time messaging between a homeowner and contractor. Only conversation participants can join. Presence tracking for online/typing status.

| Event (server -> client) | Payload |
|---|---|
| `presence_state` | current presence state -- sent on join |
| `messages:list` | `%{messages: [...]}` -- sent on join |
| `message:new` | new message map |

| Event (client -> server) | Payload |
|---|---|
| `send_message` | `%{body}` -- rate limited (10/min) |
| `typing` | `%{typing: true/false}` -- rate limited (5/3s) |

### `user:<user_id>` -- NotificationChannel

User-specific notifications and online presence. Users can only join their own channel.

| Event (server -> client) | Payload |
|---|---|
| `presence_state` | current presence state -- sent on join |
| `notification` | notification payload |

### Channel rate limiting

`FtwRealtimeWeb.RateLimiter` provides per-connection rate limiting using the process dictionary. Each channel process gets its own counters. Limits:

- `post_job`: 3 per 5 minutes
- `place_bid`: 5 per minute
- `send_message`: 10 per minute
- `typing`: 5 per 3 seconds

---

## REST API

### Pipelines and middleware

| Pipeline | Plugs |
|---|---|
| `:api` | CORS |
| `:api_login` | CORS + RateLimit (10/min) |
| `:api_register` | CORS + RateLimit (5/min) |
| `:authenticated` | CORS + Auth (JWT) |
| `:authenticated_rate_limited` | CORS + RateLimit (20/min) + Auth (JWT) |
| `:api_public_ai` | CORS + AIRateLimit (per-feature) |

### No-auth endpoints

```
GET  /api/health                              Health check (no pipeline)
GET  /api/jobs                                List open jobs
GET  /api/jobs/:id                            Job details
GET  /api/records/:public_id                  FairRecord public verification
GET  /api/records/:public_id/certificate      FairRecord printable certificate
POST /api/webhooks/persona                    Persona verification webhook
POST /api/webhooks/checkr                     Checkr background check webhook
```

### Auth endpoints (rate limited)

```
POST /api/auth/register                       Create account (5/min)
POST /api/auth/login                          Login, returns JWT (10/min)
GET  /api/auth/me                             Validate token (20/min + JWT)
```

### Public AI endpoints (AI rate limited)

```
GET  /api/ai/fair-price                       FairPrice lookup (60/min)
GET  /api/ai/stats                            FairPrice stats (60/min)
```

### Authenticated endpoints (JWT required)

```
GET    /api/users/:id                         User profile

POST   /api/push/register                     Register push notification token
DELETE /api/push/unregister                    Unregister push notification token

POST   /api/ai/estimate                       AI estimation (RunPod)
POST   /api/ai/fair-scope                     FairScope analysis

POST   /api/jobs                              Post a job
POST   /api/jobs/:id/transition               Job state transition
POST   /api/jobs/:id/bids                     Place bid
POST   /api/jobs/:id/bids/:bid_id/accept      Accept bid

GET    /api/chat/:conversation_id             Message history
POST   /api/chat/:conversation_id             Send message

GET    /api/estimates                          List estimates
GET    /api/estimates/:id                      Show estimate
POST   /api/estimates                          Create estimate
PUT    /api/estimates/:id                      Update estimate
DELETE /api/estimates/:id                      Delete estimate

GET    /api/invoices                           List invoices
GET    /api/invoices/:id                       Show invoice
POST   /api/invoices                           Create invoice
PUT    /api/invoices/:id                       Update invoice

GET    /api/projects                           List projects
GET    /api/projects/:id                       Show project
POST   /api/projects                           Create project
PUT    /api/projects/:id                       Update project

GET    /api/clients                            List clients
GET    /api/clients/:id                        Show client
POST   /api/clients                            Create client
PUT    /api/clients/:id                        Update client
DELETE /api/clients/:id                        Delete client

GET    /api/contractor/verification            Verification status
POST   /api/contractor/verification/:step      Submit verification step

GET    /api/contractors/:contractor_id/records  Contractor FairRecords
GET    /api/projects/:project_id/record         Project FairRecord
POST   /api/records/:record_id/confirm          Confirm FairRecord

GET    /api/reviews                            List reviews
GET    /api/reviews/:id                        Show review
POST   /api/reviews                            Create review
POST   /api/reviews/:id/respond                Respond to review

GET    /api/notifications                      List notifications
POST   /api/notifications/:id/read             Mark read
POST   /api/notifications/read-all             Mark all read

POST   /api/uploads                            Upload file
GET    /api/uploads                            List uploads
DELETE /api/uploads/:id                        Delete upload

GET    /api/settings                           Get user settings
PUT    /api/settings                           Update user settings
```

---

## LiveView dashboards

| Route | Module | Purpose |
|---|---|---|
| `GET /marketplace` | `MarketplaceLive` | Internal test/monitor view -- live job board and bid panel via PubSub |
| `GET /admin` | `AdminLive` | FairCommand admin dashboard -- AI costs, trust scores, ledger, system health. Password protected via `AdminAuth` plug. |

Neither is served to FTW frontend users.

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | prod | Postgres connection string. Provided by Render. |
| `SECRET_KEY_BASE` | prod | 64-byte secret. Generate with `mix phx.gen.secret`. |
| `PHX_HOST` | prod | Public hostname, e.g. `ftw-realtime.onrender.com` |
| `PHX_SERVER` | prod | Set to `"true"` to start the HTTP server on release boot. |
| `PORT` | optional | HTTP port. Defaults to `4000`. Render sets this to `10000`. |
| `POOL_SIZE` | optional | DB connection pool size. Defaults to `10`. |
| `ECTO_IPV6` | optional | Set `"true"` to use IPv6 for database connections. |
| `DNS_CLUSTER_QUERY` | optional | DNS-based node discovery for clustering. Leave unset for single-node. |
| `ADMIN_PASSWORD` | optional | Password for `/admin` dashboard. Defaults to `"faircommand"`. |
| `EXPO_ACCESS_TOKEN` | optional | Expo Push API access token for mobile push notifications. |
| `STORAGE_BUCKET` | optional | S3-compatible bucket name for file uploads. Falls back to local disk. |
| `AWS_ACCESS_KEY_ID` | optional | S3/R2 access key for file storage. |
| `AWS_SECRET_ACCESS_KEY` | optional | S3/R2 secret key for file storage. |
| `AWS_REGION` | optional | S3 region. Defaults to `us-east-1`. |
| `STORAGE_ENDPOINT` | optional | Custom S3 endpoint for R2/MinIO. |
| `SMTP_HOST` | optional | SMTP relay host for outbound email (Swoosh). |
| `SMTP_PORT` | optional | SMTP port. Defaults to `587`. |
| `SMTP_USERNAME` | optional | SMTP auth username. |
| `SMTP_PASSWORD` | optional | SMTP auth password. |

---

## Mix aliases

| Alias | What it does |
|---|---|
| `mix setup` | `deps.get` + `ecto.create` + `ecto.migrate` + seeds + install Tailwind/esbuild + build assets |
| `mix ecto.reset` | `ecto.drop` + `ecto.create` + `ecto.migrate` + seeds |
| `mix assets.build` | Compile Tailwind + esbuild bundles (unminified) |
| `mix assets.deploy` | Minified asset build + `phx.digest` for production |
| `mix precommit` | `compile --warnings-as-errors`, `deps.unlock --unused`, `format`, `test` -- run before committing |

---

## Deployment

Deployed to Render.com using the `render.yaml` config at the repo root.

- Runtime: Docker (see `Dockerfile`)
- Build: multi-stage Alpine -- Elixir 1.19.5 / Erlang 28.4.1 build image, minimal Alpine runtime image
- Plan: Starter
- Region: Oregon
- Health check: `GET /api/health`
- Database: `ftw-realtime-db` Postgres (starter plan, Oregon)
- Migrations run on startup via `FtwRealtime.Release.migrate/0`

To deploy manually:

```bash
mix assets.deploy
mix release
PHX_SERVER=true _build/prod/rel/ftw_realtime/bin/ftw_realtime start
```

Or push to the connected Render service -- it builds and deploys automatically from the `Dockerfile`.

---

## Allowed WebSocket origins

The `/socket` endpoint enforces origin checking in production:

- `https://fairtradeworker.com`
- `https://www.fairtradeworker.com`
- `https://fairtradeworker.vercel.app`
- `http://localhost:3000`
