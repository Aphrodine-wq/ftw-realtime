defmodule FtwRealtimeWeb.Api.FairPriceController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.AI.Gateway

  def show(conn, %{"category" => category, "zip" => zip, "size" => size}) do
    case Gateway.fair_price(category, zip, size) do
      {:ok, data} ->
        json(conn, %{data: data, cached: true})

      {:error, :not_found} ->
        conn |> put_status(404) |> json(%{error: "No pricing data for this combination"})

      {:error, :invalid_category} ->
        conn |> put_status(400) |> json(%{error: "Invalid category"})

      {:error, :invalid_zip} ->
        conn |> put_status(400) |> json(%{error: "Zip must be exactly 5 digits"})

      {:error, :invalid_size} ->
        conn |> put_status(400) |> json(%{error: "Size must be: small, medium, large, or major"})
    end
  end

  def show(conn, _params) do
    conn |> put_status(400) |> json(%{error: "Required params: category, zip, size"})
  end

  @doc "Admin endpoint to view cost tracking stats."
  def stats(conn, _params) do
    stats = FtwRealtime.AI.CostTracker.daily_stats()
    cache_count = FtwRealtime.AI.FairPrice.count()
    scope_count = FtwRealtime.AI.FairScope.count()

    json(conn, %{
      stats: stats,
      fair_price_cache_entries: cache_count,
      fair_scope_cache_entries: scope_count
    })
  end
end
