# This file is responsible for configuring your application
# and its dependencies with the aid of the Config module.
#
# This configuration file is loaded before any dependency and
# is restricted to this project.

# General application configuration
import Config

config :ftw_realtime,
  ecto_repos: [FtwRealtime.Repo],
  generators: [timestamp_type: :utc_datetime]

# Configure the endpoint
config :ftw_realtime, FtwRealtimeWeb.Endpoint,
  url: [host: "localhost"],
  adapter: Bandit.PhoenixAdapter,
  render_errors: [
    formats: [html: FtwRealtimeWeb.ErrorHTML, json: FtwRealtimeWeb.ErrorJSON],
    layout: false
  ],
  pubsub_server: FtwRealtime.PubSub,
  live_view: [signing_salt: "EvIWJW6F"]

# Configure esbuild (the version is required)
config :esbuild,
  version: "0.25.4",
  ftw_realtime: [
    args:
      ~w(js/app.js --bundle --target=es2022 --outdir=../priv/static/assets/js --external:/fonts/* --external:/images/* --alias:@=.),
    cd: Path.expand("../assets", __DIR__),
    env: %{"NODE_PATH" => [Path.expand("../deps", __DIR__), Mix.Project.build_path()]}
  ]

# Configure tailwind (the version is required)
config :tailwind,
  version: "4.1.12",
  ftw_realtime: [
    args: ~w(
      --input=assets/css/app.css
      --output=priv/static/assets/css/app.css
    ),
    cd: Path.expand("..", __DIR__)
  ]

# Configure Oban background jobs
config :ftw_realtime, Oban,
  repo: FtwRealtime.Repo,
  queues: [
    default: 10,
    notifications: 5,
    matching: 3,
    sync: 3
  ],
  plugins: [
    {Oban.Plugins.Cron,
     crontab: [
       # Refresh FairPrice data weekly (Sunday 3am UTC)
       {"0 3 * * 0", FtwRealtime.Workers.FairPriceComputeWorker, args: %{scope: "all"}},
       # Clean expired FairScope cache entries daily (4am UTC)
       {"0 4 * * *", FtwRealtime.Workers.FairScopeCleanupWorker},
       # Check for expired contractor verifications daily (5am UTC)
       {"0 5 * * *", FtwRealtime.Workers.VerificationExpiryWorker},
       # Recompute contractor quality scores weekly (Sunday 6am UTC)
       {"0 6 * * 0", FtwRealtime.Workers.QualityScoreWorker},
       # Daily revenue snapshot (midnight UTC)
       {"0 0 * * *", FtwRealtime.Workers.RevenueSnapshotWorker}
     ]}
  ]

# Configure Elixir's Logger
config :logger, :default_formatter,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# Import environment specific config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
