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

  scope "/", FtwRealtimeWeb do
    pipe_through :browser

    get "/", PageController, :home
    live "/marketplace", MarketplaceLive
  end

  # Health check — no pipeline, no CORS, just a 200
  get "/api/health", FtwRealtimeWeb.Api.HealthController, :index

  scope "/api", FtwRealtimeWeb.Api do
    pipe_through :api

    get "/jobs", JobController, :index
    get "/jobs/:id", JobController, :show
    post "/jobs", JobController, :create
    post "/jobs/:id/bids", JobController, :place_bid
    post "/jobs/:id/bids/:bid_id/accept", JobController, :accept_bid

    get "/chat/:conversation_id", ChatController, :index
    post "/chat/:conversation_id", ChatController, :create
  end
end
