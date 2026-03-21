defmodule FtwRealtimeWeb.Api.HealthController do
  use FtwRealtimeWeb, :controller

  def index(conn, _params) do
    json(conn, %{status: "ok", service: "ftw-realtime"})
  end
end
