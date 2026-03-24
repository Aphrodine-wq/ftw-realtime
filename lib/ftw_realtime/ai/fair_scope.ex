defmodule FtwRealtime.AI.FairScope do
  @moduledoc """
  GenServer that owns the :fair_scope_cache ETS table.

  Caches AI-generated scopes of work with a 7-day TTL.
  Key: {category, areas_hash, material_preference}
  On cache miss, calls ConstructionAI for a fresh scope and stores the result.
  """
  use GenServer

  @table :fair_scope_cache
  # 7 days in milliseconds
  @ttl_ms 7 * 24 * 60 * 60 * 1000

  # ── Client API ──────────────────────────────────────────────────────

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, :ok, name: __MODULE__)
  end

  @doc """
  Look up a cached scope or generate a fresh one.
  Returns {:ok, %{scope: string, cached: boolean}} or {:error, reason}.
  """
  def lookup(category, title, areas, materials) do
    key = cache_key(category, areas, materials)
    now = System.system_time(:millisecond)

    case :ets.lookup(@table, key) do
      [{^key, %{scope: scope, cached_at: cached_at}}] when now - cached_at < @ttl_ms ->
        FtwRealtime.AI.CostTracker.record(:fair_scope, :cache_hit)
        {:ok, %{scope: scope, cached: true}}

      _miss_or_expired ->
        case generate_scope(category, title, areas, materials) do
          {:ok, scope} ->
            :ets.insert(@table, {key, %{scope: scope, cached_at: now}})
            FtwRealtime.AI.CostTracker.record(:fair_scope, :inference)
            {:ok, %{scope: scope, cached: false}}

          {:error, reason} ->
            {:error, reason}
        end
    end
  end

  @doc "Return the number of entries in the cache."
  def count do
    :ets.info(@table, :size)
  end

  @doc "Remove entries older than TTL."
  def cleanup do
    now = System.system_time(:millisecond)
    cutoff = now - @ttl_ms

    :ets.foldl(
      fn {key, %{cached_at: cached_at}}, acc ->
        if cached_at < cutoff do
          :ets.delete(@table, key)
          acc + 1
        else
          acc
        end
      end,
      0,
      @table
    )
  end

  # ── GenServer Callbacks ─────────────────────────────────────────────

  @impl true
  def init(:ok) do
    table = :ets.new(@table, [:set, :public, :named_table, read_concurrency: true])
    {:ok, %{table: table}}
  end

  # ── Private ─────────────────────────────────────────────────────────

  defp cache_key(category, areas, materials) do
    areas_hash = areas |> Enum.sort() |> :erlang.phash2()
    {category, areas_hash, materials}
  end

  defp generate_scope(category, title, areas, materials) do
    prompt = """
    Generate a detailed scope of work for this construction project:

    Category: #{category}
    Title: #{title}
    Areas: #{Enum.join(areas, ", ")}
    Material preference: #{materials}

    Write a contractor-grade scope of work with specific line items, materials,
    and code references where applicable. Be detailed and realistic.
    Format as plain text paragraphs, not JSON.
    """

    FtwRealtime.AI.estimate(prompt)
  end
end
