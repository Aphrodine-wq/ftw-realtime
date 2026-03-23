# CLAUDE.md — ftw-realtime

Phoenix WebSocket + REST backend for FairTradeWorker. Postgres via Ecto for persistence, PubSub for real-time broadcasts.

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
  FtwRealtime.Repo             <-- Ecto Postgres
  DNSCluster
  Phoenix.PubSub (name: FtwRealtime.PubSub)
  FtwRealtimeWeb.Presence      <-- online status, typing indicators
  FtwRealtimeWeb.Endpoint
```

---

## Database

Postgres with Ecto. Five tables:

| Table | Schema | Notes |
|---|---|---|
| `users` | `Marketplace.User` | Homeowners, contractors, admins. UUID PKs. |
| `jobs` | `Marketplace.Job` | Status enum: open, bidding, awarded, in_progress, completed, disputed, cancelled |
| `bids` | `Marketplace.Bid` | One bid per contractor per job (unique constraint) |
| `conversations` | `Marketplace.Conversation` | Unique per job+homeowner+contractor triple |
| `messages` | `Marketplace.Message` | Belongs to conversation + sender |

All schemas use `binary_id` primary keys. Migrations in `priv/repo/migrations/`.

---

## Channels (lib/ftw_realtime_web/channels/)

All channels connect through `FtwRealtimeWeb.UserSocket` at `/socket`.

| Module | Topic pattern | Purpose |
|---|---|---|
| `JobChannel` | `jobs:feed` | Live job board feed |
| `BidChannel` | `job:<job_id>` | Per-job bidding and bid events |
| `ChatChannel` | `chat:<conversation_id>` | Messaging with typing indicators via Presence |
| `NotificationChannel` | `user:<user_id>` | User-specific notifications + online presence |

The socket assigns `user_id` from connect params. Defaults to `"anonymous"` — authentication not yet enforced.

---

## Context (lib/ftw_realtime/marketplace.ex)

`FtwRealtime.Marketplace` is the single context. All functions use Ecto Repo for persistence and broadcast via PubSub on state changes.

| PubSub topic | Events broadcast |
|---|---|
| `"jobs"` | `{"job:posted", job}`, `{"job:updated", job}` |
| `"job:<job_id>"` | `{"bid:placed", bid}`, `{"bid:accepted", bid}` |
| `"chat:<conversation_id>"` | `{"message:new", message}` |

Channels and `MarketplaceLive` subscribe to these topics and push/assign accordingly.

---

## REST API (lib/ftw_realtime_web/controllers/api/)

Four controllers, all under `/api`:

- `HealthController` — `GET /api/health`, no pipeline, no CORS
- `UserController` — user creation + lookup
- `JobController` — jobs CRUD + bid placement/acceptance
- `ChatController` — message list + send

The `:api` pipeline applies `FtwRealtimeWeb.Plugs.CORS`. In production, CORS allows only the three FTW origins. In dev, it allows `*`.

---

## LiveView (lib/ftw_realtime_web/live/)

`MarketplaceLive` at `/marketplace` — internal test/monitor view, not part of the FTW frontend integration. Subscribes to PubSub directly.

---

## Adding new features

**New real-time event:**
1. Add business logic to `FtwRealtime.Marketplace` — Repo query, `broadcast/3` to the right PubSub topic.
2. Add a `handle_info` clause in the relevant channel to push the event to clients.
3. If `MarketplaceLive` needs to react, add a `handle_info` there too.

**New schema:**
1. Create schema in `lib/ftw_realtime/marketplace/`.
2. Create migration in `priv/repo/migrations/`.
3. Add context functions in `marketplace.ex`.

**New channel:**
1. Create `lib/ftw_realtime_web/channels/your_channel.ex`, `use Phoenix.Channel`.
2. Register it in `UserSocket`: `channel "your:*", FtwRealtimeWeb.YourChannel`.

**New REST endpoint:**
1. Add the controller in `lib/ftw_realtime_web/controllers/api/`.
2. Add the route in `router.ex` under the `/api` scope with `pipe_through :api`.

---

## Deployment

Hosted on Render.com via Docker. `render.yaml` provisions:
- `ftw-realtime` web service (Docker, starter plan, Oregon)
- `ftw-realtime-db` Postgres database (starter plan)

The Dockerfile runs migrations on startup via `FtwRealtime.Release.migrate/0`.

---

## Environment variables

| Variable | Notes |
|---|---|
| `DATABASE_URL` | Required in prod. Provided by Render Postgres. |
| `SECRET_KEY_BASE` | Required in prod. `mix phx.gen.secret` to generate. |
| `PHX_HOST` | Required in prod. Sets the endpoint URL host. |
| `PHX_SERVER` | Set `"true"` to start the server on release boot. |
| `PORT` | HTTP port. Defaults to `4000`. Render uses `10000`. |
| `POOL_SIZE` | DB connection pool. Defaults to `10`. |
| `DNS_CLUSTER_QUERY` | Optional. Node clustering via DNS. Leave unset for single-node. |

---

## Key constraints

- Do not add LiveView routes for end-user features — this service is a WebSocket/API backend. The FTW frontend (Next.js) consumes it.
- Do not disable `mix precommit` checks. Warnings-as-errors is enforced.
- CORS origins are compile-time constants in `FtwRealtimeWeb.Plugs.CORS`. If you add a new FTW deploy URL, update that list and the `check_origin` list in `endpoint.ex`.
