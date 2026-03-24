defmodule FtwRealtimeWeb.Router do
  use FtwRealtimeWeb, :router

  pipeline :browser do
    plug :accepts, ["html"]
    plug :fetch_session
    plug :fetch_live_flash
    plug :put_root_layout, html: {FtwRealtimeWeb.Layouts, :root}
    plug :protect_from_forgery
    plug :put_secure_browser_headers
  end

  pipeline :api do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
  end

  pipeline :api_login do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
    plug FtwRealtimeWeb.Plugs.RateLimit, limit: 10, window_ms: 60_000
  end

  pipeline :api_register do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
    plug FtwRealtimeWeb.Plugs.RateLimit, limit: 5, window_ms: 60_000
  end

  pipeline :authenticated do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
    plug FtwRealtimeWeb.Plugs.Auth
  end

  pipeline :api_public_ai do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
    plug FtwRealtimeWeb.Plugs.AIRateLimit, feature: :fair_price
  end

  pipeline :authenticated_rate_limited do
    plug :accepts, ["json"]
    plug FtwRealtimeWeb.Plugs.CORS
    plug FtwRealtimeWeb.Plugs.RateLimit, limit: 20, window_ms: 60_000
    plug FtwRealtimeWeb.Plugs.Auth
  end

  scope "/", FtwRealtimeWeb do
    pipe_through :browser

    get "/", PageController, :home
    live "/marketplace", MarketplaceLive
  end

  # Admin dashboard — session-based password auth
  scope "/", FtwRealtimeWeb do
    pipe_through [:browser, FtwRealtimeWeb.Plugs.AdminAuth]

    live "/admin", AdminLive
  end

  # Health check — no pipeline
  get "/api/health", FtwRealtimeWeb.Api.HealthController, :index

  # Auth endpoints — rate limited
  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :api_login
    post "/auth/login", AuthController, :login
  end

  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :api_register
    post "/auth/register", UserController, :create
  end

  # Public AI endpoints — rate limited, no auth
  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :api_public_ai

    get "/ai/fair-price", FairPriceController, :show
    get "/ai/stats", FairPriceController, :stats
  end

  # Public API — no auth required
  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :api

    # Job browsing is public
    get "/jobs", JobController, :index
    get "/jobs/:id", JobController, :show
  end

  # Token validation — rate limited + authenticated
  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :authenticated_rate_limited
    get "/auth/me", AuthController, :me
  end

  # Authenticated API — JWT required
  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :authenticated

    get "/users/:id", UserController, :show

    post "/ai/estimate", AIController, :estimate
    post "/ai/fair-scope", FairScopeController, :create

    post "/jobs", JobController, :create
    post "/jobs/:id/transition", JobController, :transition
    post "/jobs/:id/bids", JobController, :place_bid
    post "/jobs/:id/bids/:bid_id/accept", JobController, :accept_bid

    get "/chat/:conversation_id", ChatController, :index
    post "/chat/:conversation_id", ChatController, :create

    # Estimates
    resources "/estimates", EstimateController, only: [:index, :show, :create, :update, :delete]

    # Invoices
    resources "/invoices", InvoiceController, only: [:index, :show, :create, :update]

    # Projects
    resources "/projects", ProjectController, only: [:index, :show, :create, :update]

    # Clients
    resources "/clients", ClientController, only: [:index, :show, :create, :update, :delete]

    # Reviews
    get "/reviews", ReviewController, :index
    get "/reviews/:id", ReviewController, :show
    post "/reviews", ReviewController, :create
    post "/reviews/:id/respond", ReviewController, :respond

    # Notifications
    get "/notifications", NotificationController, :index
    post "/notifications/:id/read", NotificationController, :mark_read
    post "/notifications/read-all", NotificationController, :mark_all_read

    # Uploads
    post "/uploads", UploadController, :create
    get "/uploads", UploadController, :index
    delete "/uploads/:id", UploadController, :delete

    # Settings
    get "/settings", SettingsController, :show
    put "/settings", SettingsController, :update
  end
end
