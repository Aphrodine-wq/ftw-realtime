defmodule FtwRealtimeWeb.Plugs.RateLimit do
  @moduledoc """
  Simple ETS-based rate limiting plug.

  Tracks requests per IP + path in an ETS table with a sliding window.
  Returns 429 with Retry-After header when the limit is exceeded.

  ## Options

    * `:limit` - max requests per window (default: 10)
    * `:window_ms` - window duration in milliseconds (default: 60_000)

  ## Usage

      plug FtwRealtimeWeb.Plugs.RateLimit, limit: 5, window_ms: 60_000
  """
  import Plug.Conn

  @table :rate_limit_buckets
  @default_limit 10
  @default_window_ms 60_000

  def init(opts) do
    ensure_table_exists()

    %{
      limit: Keyword.get(opts, :limit, @default_limit),
      window_ms: Keyword.get(opts, :window_ms, @default_window_ms)
    }
  end

  def call(conn, %{limit: limit, window_ms: window_ms}) do
    key = rate_limit_key(conn)
    now = System.system_time(:millisecond)

    case check_rate(key, limit, window_ms, now) do
      {:ok, remaining} ->
        conn
        |> put_resp_header("x-ratelimit-limit", to_string(limit))
        |> put_resp_header("x-ratelimit-remaining", to_string(remaining))

      {:error, retry_after} ->
        retry_seconds = max(div(retry_after, 1000), 1)

        conn
        |> put_resp_header("retry-after", to_string(retry_seconds))
        |> put_resp_header("x-ratelimit-limit", to_string(limit))
        |> put_resp_header("x-ratelimit-remaining", "0")
        |> put_resp_header("content-type", "application/json")
        |> send_resp(
          429,
          Jason.encode!(%{error: "Too many requests", retry_after: retry_seconds})
        )
        |> halt()
    end
  end

  defp rate_limit_key(conn) do
    ip = conn.remote_ip |> :inet.ntoa() |> to_string()
    path = conn.request_path
    "#{ip}:#{path}"
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
