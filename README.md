# ftw-realtime

Phoenix WebSocket backend for FairTradeWorker. Handles live bidding, real-time job feed updates, and contractor/homeowner chat. Deployed to Render.com via Docker.

- **Stack:** Elixir 1.15+, Phoenix 1.8, Bandit HTTP server, Phoenix PubSub
- **State:** In-memory GenServer (`FtwRealtime.Marketplace`) — no database
- **Transport:** Phoenix Channels over WebSocket + Phoenix LiveView for the internal dashboard
- **Deployed at:** `https://ftw-realtime.onrender.com`

---

## Local development

```bash
mix setup
```

```bash
mix phx.server
```

Or inside IEx:

```bash
iex -S mix phx.server
```

The server starts on `http://localhost:4000`. The live marketplace dashboard is at `http://localhost:4000/marketplace`.

---

## WebSocket channels

Connect to `/socket` via Phoenix Socket. Pass `user_id` as a connect param — it defaults to `"anonymous"` if omitted.

### `jobs:feed` — JobChannel

Subscribe to the live job board.

| Event (server -> client) | Payload |
|---|---|
| `jobs:list` | `%{jobs: [...]}` — sent on join |
| `job:posted` | `%{id, title, description, ...}` |
| `job:updated` | updated job map (e.g. after a bid is placed or accepted) |

| Event (client -> server) | Payload |
|---|---|
| `post_job` | `%{title, description, category, location, budget_min, budget_max, homeowner}` |

### `job:<job_id>` — BidChannel

Subscribe to a specific job for live bid activity.

| Event (server -> client) | Payload |
|---|---|
| `job:details` | `%{job: ..., bids: [...]}` — sent on join |
| `bid:placed` | new bid map |
| `bid:accepted` | accepted bid map |

| Event (client -> server) | Payload |
|---|---|
| `place_bid` | `%{contractor, amount, message, timeline}` |
| `accept_bid` | `%{bid_id: "bid_123"}` |

### `chat:<conversation_id>` — ChatChannel

Real-time messaging between a homeowner and contractor.

| Event (server -> client) | Payload |
|---|---|
| `messages:list` | `%{messages: [...]}` — sent on join |
| `message:new` | new message map |

| Event (client -> server) | Payload |
|---|---|
| `send_message` | `%{sender, body}` |

---

## REST API

The `:api` pipeline applies the `FtwRealtimeWeb.Plugs.CORS` plug. In production, CORS is restricted to `fairtradeworker.com`, `www.fairtradeworker.com`, and `fairtradeworker.vercel.app`. In dev, all origins are allowed.

### Health

```
GET /api/health
```

Returns `{"status": "ok", "service": "ftw-realtime"}`. No auth, no CORS — used by Render health checks.

### Jobs

```
GET    /api/jobs
GET    /api/jobs/:id
POST   /api/jobs                        body: {"job": {...}}
POST   /api/jobs/:id/bids               body: {"bid": {...}}
POST   /api/jobs/:id/bids/:bid_id/accept
```

### Chat

```
GET  /api/chat/:conversation_id
POST /api/chat/:conversation_id         body: {"message": {...}}
```

---

## LiveView dashboard

`GET /marketplace` — `FtwRealtimeWeb.MarketplaceLive`

An internal Phoenix LiveView connected to PubSub. Shows the live job board and bid panel. Intended for internal testing/monitoring, not served to FTW frontend users.

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `SECRET_KEY_BASE` | prod | 64-byte secret. Generate with `mix phx.gen.secret`. Render auto-generates this. |
| `PHX_HOST` | prod | Public hostname, e.g. `ftw-realtime.onrender.com` |
| `PHX_SERVER` | prod | Set to `"true"` to start the HTTP server on release boot |
| `PORT` | optional | HTTP port. Defaults to `4000`. Render sets this to `10000`. |
| `DNS_CLUSTER_QUERY` | optional | DNS-based node discovery for clustering. Leave unset for single-node. |

---

## Mix aliases

| Alias | What it does |
|---|---|
| `mix setup` | `deps.get` + install Tailwind/esbuild + build assets |
| `mix assets.build` | Compile Tailwind + esbuild bundles (unminified) |
| `mix assets.deploy` | Minified asset build + `phx.digest` for production |
| `mix precommit` | `compile --warnings-as-errors`, `deps.unlock --unused`, `format`, `test` — run before committing |

---

## Deployment

Deployed to Render.com using the `render.yaml` config at the repo root.

- Runtime: Docker (see `Dockerfile`)
- Build: multi-stage Alpine — Elixir 1.19.5 / Erlang 28.4.1 build image, minimal Alpine runtime image
- Plan: Starter
- Region: Oregon
- Health check: `GET /api/health`

To deploy manually:

```bash
mix assets.deploy
mix release
PHX_SERVER=true _build/prod/rel/ftw_realtime/bin/ftw_realtime start
```

Or push to the connected Render service — it builds and deploys automatically from the `Dockerfile`.

---

## State and persistence

`FtwRealtime.Marketplace` is an OTP GenServer that holds all state in memory. There is no database. State resets on each process restart/deploy. The server seeds four demo jobs on startup (Oxford MS, Tupelo MS, Jackson MS, Starkville MS).

This is intentional for launch — the FTW frontend is still running on mock data. The in-memory design keeps the stack minimal until the database layer is added.

---

## Allowed WebSocket origins

The `/socket` endpoint enforces origin checking in production:

- `https://fairtradeworker.com`
- `https://www.fairtradeworker.com`
- `https://fairtradeworker.vercel.app`
- `http://localhost:3000`
