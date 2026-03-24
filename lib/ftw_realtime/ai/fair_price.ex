defmodule FtwRealtime.AI.FairPrice do
  @moduledoc """
  GenServer that owns the :fair_price_cache ETS table.

  Pre-computed FairPrice data is loaded from Postgres on boot and served
  via lock-free ETS reads. The table is keyed by {category, zip_prefix_3digit, size}.

  Texas zip prefixes: 730-799 (~50 prefixes).
  12 categories x 50 prefixes x 4 sizes = 2,400 entries.
  """
  use GenServer

  @table :fair_price_cache

  @categories [
    "General Contracting",
    "Plumbing",
    "Electrical",
    "HVAC",
    "Roofing",
    "Painting",
    "Flooring",
    "Landscaping",
    "Remodeling",
    "Concrete",
    "Fencing",
    "Drywall"
  ]

  @sizes ["small", "medium", "large", "major"]

  # Texas 3-digit zip prefixes (730-799)
  @tx_prefixes Enum.map(730..799, &to_string/1)

  def categories, do: @categories
  def sizes, do: @sizes
  def tx_prefixes, do: @tx_prefixes

  # ── Client API ──────────────────────────────────────────────────────

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, :ok, name: __MODULE__)
  end

  @doc "Look up a pre-computed FairPrice estimate. Returns {:ok, map} or {:error, :not_found}."
  def lookup(category, zip, size) do
    prefix = String.slice(zip, 0, 3)

    case :ets.lookup(@table, {category, prefix, size}) do
      [{_key, data}] -> {:ok, data}
      [] -> {:error, :not_found}
    end
  end

  @doc "Insert or update a single entry in the cache."
  def put(category, zip_prefix, size, data) do
    :ets.insert(@table, {{category, zip_prefix, size}, data})
    :ok
  end

  @doc "Reload all entries from Postgres into ETS."
  def refresh do
    GenServer.call(__MODULE__, :refresh, 30_000)
  end

  @doc "Return the number of entries in the cache."
  def count do
    :ets.info(@table, :size)
  end

  # ── GenServer Callbacks ─────────────────────────────────────────────

  @impl true
  def init(:ok) do
    table = :ets.new(@table, [:set, :public, :named_table, read_concurrency: true])

    # Load from DB if the table exists; skip gracefully on fresh installs
    try do
      load_from_db()
    rescue
      _ -> :ok
    end

    {:ok, %{table: table}}
  end

  @impl true
  def handle_call(:refresh, _from, state) do
    count = load_from_db()
    {:reply, {:ok, count}, state}
  end

  defp load_from_db do
    alias FtwRealtime.AI.FairPriceEntry
    alias FtwRealtime.Repo
    import Ecto.Query

    entries = Repo.all(from(e in FairPriceEntry, select: e))

    Enum.each(entries, fn entry ->
      data = %{
        low: entry.low,
        high: entry.high,
        materials_pct: entry.materials_pct,
        labor_pct: entry.labor_pct,
        confidence: entry.confidence,
        computed_at: entry.updated_at
      }

      :ets.insert(@table, {{entry.category, entry.zip_prefix, entry.size}, data})
    end)

    length(entries)
  end
end
