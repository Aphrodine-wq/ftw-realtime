defmodule FtwRealtime.AI.CostTracker do
  @moduledoc """
  ETS-backed cost tracking for AI inference calls.

  Tracks cache hits and inference calls per feature per day using atomic
  `:ets.update_counter`. Exposes daily stats for monitoring.
  """
  use GenServer

  @table :ai_cost_tracker
  # $0.002 = 0.2 cents
  @cost_per_inference_cents 0.2

  # ── Client API ──────────────────────────────────────────────────────

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, :ok, name: __MODULE__)
  end

  @doc "Record a cache hit or inference call for a feature."
  def record(feature, type) when type in [:cache_hit, :inference] do
    day = Date.utc_today() |> Date.to_iso8601()
    key = {feature, day, type}

    try do
      :ets.update_counter(@table, key, {2, 1})
    catch
      :error, :badarg ->
        :ets.insert_new(@table, {key, 0})
        :ets.update_counter(@table, key, {2, 1})
    end

    :ok
  end

  @doc "Get daily stats for all features."
  def daily_stats do
    day = Date.utc_today() |> Date.to_iso8601()

    [:fair_price, :fair_scope, :estimate_agent]
    |> Enum.map(fn feature ->
      hits = get_count(feature, day, :cache_hit)
      inferences = get_count(feature, day, :inference)

      {feature,
       %{
         cache_hits: hits,
         inferences: inferences,
         total_requests: hits + inferences,
         estimated_cost_cents: Float.round(inferences * @cost_per_inference_cents, 2)
       }}
    end)
    |> Map.new()
  end

  @doc "Reset all counters."
  def reset do
    :ets.delete_all_objects(@table)
    :ok
  end

  # ── GenServer Callbacks ─────────────────────────────────────────────

  @impl true
  def init(:ok) do
    table = :ets.new(@table, [:set, :public, :named_table, write_concurrency: true])
    {:ok, %{table: table}}
  end

  defp get_count(feature, day, type) do
    case :ets.lookup(@table, {feature, day, type}) do
      [{_key, count}] -> count
      [] -> 0
    end
  end
end
