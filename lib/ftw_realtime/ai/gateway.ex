defmodule FtwRealtime.AI.Gateway do
  @moduledoc """
  Central routing module for all AI inference in FTW.

  Routes requests through three tiers:
  - FairPrice: pre-computed ETS lookup ($0/request)
  - FairScope: ETS cache with 7-day TTL ($0.002 on miss)
  - EstimateAgent: always model inference ($0.002/turn, Pro-only)
  """

  alias FtwRealtime.AI.{FairPrice, FairScope, CostTracker}

  @valid_sizes ["small", "medium", "large", "major"]

  @doc "Look up a pre-computed FairPrice estimate."
  def fair_price(category, zip, size) do
    with :ok <- validate_category(category),
         :ok <- validate_zip(zip),
         :ok <- validate_size(size) do
      case FairPrice.lookup(category, zip, size) do
        {:ok, data} ->
          CostTracker.record(:fair_price, :cache_hit)
          {:ok, data}

        {:error, :not_found} ->
          {:error, :not_found}
      end
    end
  end

  @doc "Generate or retrieve a cached scope of work."
  def fair_scope(category, title, areas, materials) do
    with :ok <- validate_category(category),
         true <- (is_binary(title) and byte_size(title) > 0) || {:error, :invalid_title},
         true <- is_list(areas) || {:error, :invalid_areas} do
      FairScope.lookup(category, title, areas, materials || "Mid-range")
    end
  end

  @doc "Run a conversational estimate via ConstructionAI. Always hits the model."
  def estimate_agent(description, opts \\ []) do
    CostTracker.record(:estimate_agent, :inference)
    FtwRealtime.AI.estimate(description, opts)
  end

  # ── Validation ──────────────────────────────────────────────────────

  defp validate_category(category) do
    if category in FairPrice.categories(), do: :ok, else: {:error, :invalid_category}
  end

  defp validate_zip(zip) when is_binary(zip) and byte_size(zip) == 5, do: :ok
  defp validate_zip(_), do: {:error, :invalid_zip}

  defp validate_size(size) when size in @valid_sizes, do: :ok
  defp validate_size(_), do: {:error, :invalid_size}
end
