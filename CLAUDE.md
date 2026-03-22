# CLAUDE.md — ftw-realtime

This is the Phoenix WebSocket backend for FairTradeWorker. No database. All state lives in the `FtwRealtime.Marketplace` GenServer.

---

## Running the project

```bash
mix setup
mix phx.server
```

Before committing anything:

```bash
mix precommit
```

This runs `compile --warnings-as-errors`, `deps.unlock --unused`, `format`, and `test`. Fix everything it reports before pushing.

---

## Architecture

```
FtwRealtime.Application (supervisor)
  FtwRealtimeWeb.Telemetry
  DNSCluster
  Phoenix.PubSub (name: FtwRealtime.PubSub)
  FtwRealtime.Marketplace   <-- GenServer, all runtime state
  FtwRealtimeWeb.Endpoint
```

There is no Ecto, no Repo, no database. Do not add one without discussing it first.

---

## Channels (lib/ftw_realtime_web/channels/)

All three channels connect through `FtwRealtimeWeb.UserSocket` at `/socket`.

| Module | Topic pattern | Purpose |
|---|---|---|
| `JobChannel` | `jobs:feed` | Live job board feed |
| `BidChannel` | `job:<job_id>` | Per-job bidding and bid events |
| `ChatChannel` | `chat:<conversation_id>` | Contractor/homeowner messaging |

The socket assigns `user_id` from connect params. It defaults to `"anonymous"` — authentication is not yet enforced.

---

## Context (lib/ftw_realtime/marketplace.ex)

`FtwRealtime.Marketplace` is the single context. It is a named GenServer. All public functions are synchronous `GenServer.call/2` wrappers.

When state changes, it broadcasts via `Phoenix.PubSub` on these topics:

| PubSub topic | Events broadcast |
|---|---|
| `"jobs"` | `{"job:posted", job}`, `{"job:updated", job}` |
| `"job:<job_id>"` | `{"bid:placed", bid}`, `{"bid:accepted", bid}` |
| `"chat:<conversation_id>"` | `{"message:new", message}` |

Channels and `MarketplaceLive` subscribe to these topics and push/assign accordingly.

---

## REST API (lib/ftw_realtime_web/controllers/api/)

Three controllers, all under `/api`:

- `HealthController` — `GET /api/health`, no pipeline, no CORS
- `JobController` — jobs CRUD + bid placement/acceptance
- `ChatController` — message list + send

The `:api` pipeline applies `FtwRealtimeWeb.Plugs.CORS`. In production, CORS allows only the three FTW origins. In dev, it allows `*`.

---

## LiveView (lib/ftw_realtime_web/live/)

`MarketplaceLive` at `/marketplace` — internal test/monitor view, not part of the FTW frontend integration. It subscribes to PubSub directly and mirrors everything the channels do.

---

## Adding new features

**New real-time event:**
1. Add the business logic to `FtwRealtime.Marketplace` — handle the `GenServer.call`, mutate state, `broadcast/3` to the right PubSub topic.
2. Add a `handle_info` clause in the relevant channel to push the event to clients.
3. If `MarketplaceLive` needs to react, add a `handle_info` there too.

**New channel:**
1. Create `lib/ftw_realtime_web/channels/your_channel.ex`, `use Phoenix.Channel`.
2. Register it in `UserSocket`: `channel "your:*", FtwRealtimeWeb.YourChannel`.

**New REST endpoint:**
1. Add the controller in `lib/ftw_realtime_web/controllers/api/`.
2. Add the route in `router.ex` under the `/api` scope with `pipe_through :api`.

---

## Environment variables

| Variable | Notes |
|---|---|
| `SECRET_KEY_BASE` | Required in prod. `mix phx.gen.secret` to generate. |
| `PHX_HOST` | Required in prod. Sets the endpoint URL host. |
| `PHX_SERVER` | Set `"true"` to start the server on release boot. |
| `PORT` | HTTP port. Defaults to `4000`. Render uses `10000`. |
| `DNS_CLUSTER_QUERY` | Optional. Node clustering via DNS. Leave unset for single-node. |

---

## HTTP client

Use `Req` for any outbound HTTP. It is included in deps. Do not add `:httpoison`, `:tesla`, or `:httpc`.

---

## Key constraints

- No database, no Ecto. State is in-memory only. Restarts wipe state.
- Do not add LiveView routes for end-user features — this service is a WebSocket/API backend. The FTW frontend (Next.js) consumes it.
- Do not disable `mix precommit` checks. Warnings-as-errors is enforced.
- CORS origins are compile-time constants in `FtwRealtimeWeb.Plugs.CORS`. If you add a new FTW deploy URL, update that list and the `check_origin` list in `endpoint.ex`.
