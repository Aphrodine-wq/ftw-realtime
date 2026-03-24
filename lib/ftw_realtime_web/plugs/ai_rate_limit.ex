defmodule FtwRealtimeWeb.Plugs.AIRateLimit do
  @moduledoc """
  Per-feature AI rate limiting.

  Keys on user_id:feature for authenticated endpoints,
  IP:feature for public endpoints. Separate limits per feature.

  ## Options
    * `:feature` - atom identifying the AI feature (:fair_price, :fair_scope, :estimate_agent)
    * `:limit` - max requests per window (default varies by feature)
    * `:window_ms` - window duration in milliseconds (default: 60_000)
  """
  import Plug.Conn

  @table :ai_rate_limit_buckets

  @default_limits %{
    fair_price: 60,
    fair_scope: 10,
    estimate_agent: 5
  }

  def init(opts) do
    ensure_table_exists()
    feature = Keyword.fetch!(opts, :feature)

    %{
      feature: feature,
      limit: Keyword.get(opts, :limit, Map.get(@default_limits, feature, 10)),
      window_ms: Keyword.get(opts, :window_ms, 60_000)
    }
  end

  def call(conn, %{feature: feature, limit: limit, window_ms: window_ms}) do
    key = rate_key(conn, feature)
    now = System.system_time(:millisecond)

    case check_rate(key, limit, window_ms, now) do
      {:ok, remaining} ->
        conn
        |> put_resp_header("x-ratelimit-limit", to_string(limit))
        |> put_resp_header("x-ratelimit-remaining", to_string(remaining))
        |> put_resp_header("x-ratelimit-feature", to_string(feature))

      {:error, retry_after} ->
        retry_seconds = max(div(retry_after, 1000), 1)

        conn
        |> put_resp_header("retry-after", to_string(retry_seconds))
        |> put_resp_header("x-ratelimit-limit", to_string(limit))
        |> put_resp_header("x-ratelimit-remaining", "0")
        |> put_resp_header("content-type", "application/json")
        |> send_resp(
          429,
          Jason.encode!(%{
            error: "Too many requests",
            feature: feature,
            retry_after: retry_seconds
          })
        )
        |> halt()
    end
  end

  defp rate_key(conn, feature) do
    identity =
      case conn.assigns[:current_user_id] do
        nil -> conn.remote_ip |> :inet.ntoa() |> to_string()
        user_id -> user_id
      end

    "ai:#{identity}:#{feature}"
  end

  defp check_rate(key, limit, window_ms, now) do
    case :ets.lookup(@table, key) do
      [{^key, count, reset_time}] when now < reset_time ->
        if count >= limit do
          {:error, reset_time - now}
        else
          :ets.update_counter(@table, key, {2, 1})
          {:ok, limit - count - 1}
        end

      _expired_or_missing ->
        :ets.insert(@table, {key, 1, now + window_ms})
        {:ok, limit - 1}
    end
  end

  defp ensure_table_exists do
    if :ets.whereis(@table) == :undefined do
      :ets.new(@table, [:set, :public, :named_table])
    end
  end
end
