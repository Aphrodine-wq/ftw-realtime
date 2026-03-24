defmodule FtwRealtimeWeb.Api.FairScopeController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.AI.Gateway

  def create(conn, %{"category" => category, "title" => title} = params) do
    areas = Map.get(params, "areas", [])
    materials = Map.get(params, "materials", "Mid-range")

    case Gateway.fair_scope(category, title, areas, materials) do
      {:ok, %{scope: scope, cached: cached}} ->
        json(conn, %{data: %{scope: scope}, cached: cached})

      {:error, :invalid_category} ->
        conn |> put_status(400) |> json(%{error: "Invalid category"})

      {:error, reason} ->
        conn
        |> put_status(503)
        |> json(%{error: "Scope generation failed", reason: inspect(reason)})
    end
  end

  def create(conn, _params) do
    conn |> put_status(400) |> json(%{error: "Required params: category, title"})
  end
end
