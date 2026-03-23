defmodule FtwRealtimeWeb.RateLimiter do
  @moduledoc """
  Per-connection channel rate limiting using process dictionary.
  Each channel process gets its own counters — no shared state, no bottleneck.

  Usage in a channel:
      alias FtwRealtimeWeb.RateLimiter

      def handle_in("send_message", attrs, socket) do
        case RateLimiter.check(:message, limit: 10, window: 60_000) do
          :ok -> # proceed
          :rate_limited -> {:reply, {:error, %{reason: "rate limited"}}, socket}
        end
      end
  """

  @doc """
  Check if the action is within rate limits.
  - `key` — atom identifying the action (e.g., :message, :bid)
  - `limit` — max actions per window
  - `window` — window size in milliseconds
  """
  def check(key, opts) do
    limit = Keyword.fetch!(opts, :limit)
    window = Keyword.fetch!(opts, :window)
    now = System.monotonic_time(:millisecond)
    dict_key = {:rate_limit, key}

    {timestamps, _} = Process.get(dict_key, {[], 0})

    # Drop timestamps outside the window
    active = Enum.filter(timestamps, fn ts -> now - ts < window end)

    if length(active) >= limit do
      :rate_limited
    else
      Process.put(dict_key, {[now | active], length(active) + 1})
      :ok
    end
  end
end
