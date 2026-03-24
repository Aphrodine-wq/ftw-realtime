defmodule FtwRealtime.Workers.FairPriceComputeWorker do
  @moduledoc """
  Oban worker that pre-computes FairPrice data for all category/zip/size combinations.

  Iterates through the matrix, calls ConstructionAI for each combo,
  and upserts results into Postgres + ETS. Self-rate-limits to avoid
  overwhelming RunPod: batches of 10 with 500ms gaps.

  ## Args
    - %{"scope" => "all"} — compute all 2,400 combinations
    - %{"scope" => "category", "category" => "Roofing"} — one category
  """
  use Oban.Worker, queue: :sync, max_attempts: 3, unique: [period: 3600]

  alias FtwRealtime.AI.{FairPrice, FairPriceEntry}
  alias FtwRealtime.Repo

  @batch_size 10
  @batch_delay_ms 500

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"scope" => "all"}}) do
    combos =
      for category <- FairPrice.categories(),
          prefix <- FairPrice.tx_prefixes(),
          size <- FairPrice.sizes(),
          do: {category, prefix, size}

    process_combos(combos)
  end

  def perform(%Oban.Job{args: %{"scope" => "category", "category" => category}}) do
    combos =
      for prefix <- FairPrice.tx_prefixes(),
          size <- FairPrice.sizes(),
          do: {category, prefix, size}

    process_combos(combos)
  end

  def perform(_job), do: :ok

  defp process_combos(combos) do
    combos
    |> Enum.chunk_every(@batch_size)
    |> Enum.each(fn batch ->
      Enum.each(batch, &compute_and_store/1)
      Process.sleep(@batch_delay_ms)
    end)

    # Refresh ETS from DB after all writes
    FairPrice.refresh()
    :ok
  end

  defp compute_and_store({category, prefix, size}) do
    prompt = """
    For a #{size} #{String.downcase(category)} project in Texas zip code area #{prefix}XX,
    estimate the typical cost range. Return ONLY a JSON object:
    {"low": dollars_int, "high": dollars_int, "materials_pct": 0.0_to_1.0, "labor_pct": 0.0_to_1.0, "confidence": "high"|"medium"|"low"}
    No other text.
    """

    case FtwRealtime.AI.estimate(prompt) do
      {:ok, content} ->
        case parse_price_response(content) do
          {:ok, data} -> upsert_entry(category, prefix, size, data, content)
          # skip unparseable responses
          {:error, _} -> :ok
        end

      {:error, _} ->
        # skip failed inference calls
        :ok
    end
  end

  defp parse_price_response(content) do
    # Try to extract JSON from the response
    with {:ok, json} <- extract_json(content),
         {:ok, decoded} <- Jason.decode(json) do
      {:ok,
       %{
         low: round_to_int(decoded["low"]),
         high: round_to_int(decoded["high"]),
         materials_pct: decoded["materials_pct"] || 0.35,
         labor_pct: decoded["labor_pct"] || 0.50,
         confidence: decoded["confidence"] || "medium"
       }}
    end
  end

  defp extract_json(content) do
    case Regex.run(~r/\{[^}]+\}/, content) do
      [json] -> {:ok, json}
      _ -> {:error, :no_json}
    end
  end

  defp round_to_int(val) when is_float(val), do: round(val)
  defp round_to_int(val) when is_integer(val), do: val
  defp round_to_int(_), do: 0

  defp upsert_entry(category, prefix, size, data, raw) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    Repo.insert!(
      %FairPriceEntry{
        category: category,
        zip_prefix: prefix,
        size: size,
        low: data.low,
        high: data.high,
        materials_pct: data.materials_pct,
        labor_pct: data.labor_pct,
        confidence: data.confidence,
        raw_response: raw
      }
      |> Map.put(:inserted_at, now)
      |> Map.put(:updated_at, now),
      on_conflict:
        {:replace,
         [:low, :high, :materials_pct, :labor_pct, :confidence, :raw_response, :updated_at]},
      conflict_target: [:category, :zip_prefix, :size]
    )

    # Also update ETS immediately
    FairPrice.put(category, prefix, size, data)
  end
end
