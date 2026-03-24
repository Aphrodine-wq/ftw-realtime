defmodule FtwRealtime.AI.EstimateAgent do
  @moduledoc """
  Thin wrapper for conversational estimate generation.
  Always calls the model — no caching. Pro-tier only.
  """

  alias FtwRealtime.AI.CostTracker

  @doc "Generate a detailed estimate from a description. Always hits the model."
  def generate(description, opts \\ []) do
    CostTracker.record(:estimate_agent, :inference)
    FtwRealtime.AI.estimate(description, opts)
  end
end
