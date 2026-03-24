defmodule FtwRealtime.AI.RunpodHealth do
  @moduledoc """
  Client for RunPod Serverless health monitoring.

  Polls the /health endpoint to get worker stats, queue depth,
  and job counts for the ConstructionAI endpoint.

  Requires env vars:
  - RUNPOD_API_KEY — RunPod API key (rpa_...)
  - RUNPOD_ENDPOINT_ID — Serverless endpoint ID
  """

  @base_url "https://api.runpod.ai/v2"
  @timeout 10_000

  @doc """
  Fetch health status from RunPod. Returns {:ok, map} or {:error, reason}.

  Response includes:
  - workers: %{idle: n, initializing: n, ready: n, running: n, throttled: n}
  - jobs: %{completed: n, failed: n, in_progress: n, in_queue: n, retried: n}
  """
  def check do
    case {api_key(), endpoint_id()} do
      {nil, _} -> {:error, :no_api_key}
      {_, nil} -> {:error, :no_endpoint_id}
      {key, eid} -> fetch_health(key, eid)
    end
  end

  @doc "Returns whether RunPod monitoring is configured."
  def configured? do
    api_key() != nil and endpoint_id() != nil
  end

  @doc "Returns a summary map even if RunPod is not configured."
  def status do
    case check() do
      {:ok, data} ->
        %{
          connected: true,
          workers: data["workers"] || %{},
          jobs: data["jobs"] || %{},
          endpoint_id: endpoint_id(),
          checked_at: DateTime.utc_now()
        }

      {:error, reason} ->
        %{
          connected: false,
          error: reason,
          endpoint_id: endpoint_id(),
          checked_at: DateTime.utc_now()
        }
    end
  end

  defp fetch_health(api_key, endpoint_id) do
    url = "#{@base_url}/#{endpoint_id}/health"

    case :httpc.request(
           :get,
           {to_charlist(url), [{~c"authorization", to_charlist("Bearer #{api_key}")}]},
           [timeout: @timeout, connect_timeout: 5_000],
           []
         ) do
      {:ok, {{_, 200, _}, _, body}} ->
        case Jason.decode(to_string(body)) do
          {:ok, data} -> {:ok, data}
          {:error, _} -> {:error, :invalid_response}
        end

      {:ok, {{_, 401, _}, _, _}} ->
        {:error, :unauthorized}

      {:ok, {{_, status, _}, _, _}} ->
        {:error, {:http_error, status}}

      {:error, reason} ->
        {:error, reason}
    end
  end

  defp api_key, do: System.get_env("RUNPOD_API_KEY")
  defp endpoint_id, do: System.get_env("RUNPOD_ENDPOINT_ID")
end
