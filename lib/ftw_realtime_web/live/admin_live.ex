defmodule FtwRealtimeWeb.AdminLive do
  use FtwRealtimeWeb, :live_view

  alias FtwRealtime.AI.{FairPrice, FairScope, CostTracker, RunpodHealth}
  alias FtwRealtime.Ops.{FairTrust, FairLedger}
  alias FtwRealtime.Repo

  import Ecto.Query

  @refresh_interval 15_000

  @impl true
  def mount(_params, _session, socket) do
    if connected?(socket) do
      Process.send_after(self(), :refresh, @refresh_interval)
    end

    socket =
      socket
      |> assign(:page_title, "FairCommand")
      |> assign_stats()

    {:ok, socket}
  end

  @impl true
  def handle_info(:refresh, socket) do
    Process.send_after(self(), :refresh, @refresh_interval)
    {:noreply, assign_stats(socket)}
  end

  @impl true
  def handle_event("refresh_fairprice", _params, socket) do
    FtwRealtime.Workers.FairPriceComputeWorker.new(%{scope: "all"})
    |> Oban.insert()

    {:noreply,
     socket
     |> put_flash(:info, "FairPrice refresh job queued")
     |> assign_stats()}
  end

  @impl true
  def handle_event("cleanup_fairscope", _params, socket) do
    removed = FairScope.cleanup()

    {:noreply,
     socket
     |> put_flash(:info, "Cleaned #{removed} expired FairScope entries")
     |> assign_stats()}
  end

  @impl true
  def handle_event("reset_cost_tracker", _params, socket) do
    CostTracker.reset()

    {:noreply,
     socket
     |> put_flash(:info, "Cost tracker reset")
     |> assign_stats()}
  end

  defp assign_stats(socket) do
    socket
    |> assign(:system, system_stats())
    |> assign(:business, business_stats())
    |> assign(:ai, ai_stats())
    |> assign(:runpod, RunpodHealth.status())
    |> assign(:oban, oban_stats())
    |> assign(:trust, trust_stats())
    |> assign(:ledger, ledger_stats())
    |> assign(:updated_at, DateTime.utc_now() |> Calendar.strftime("%H:%M:%S UTC"))
  end

  defp system_stats do
    memory = :erlang.memory()

    %{
      uptime: uptime(),
      total_memory_mb: div(memory[:total], 1_048_576),
      process_count: :erlang.system_info(:process_count),
      node: node()
    }
  end

  defp business_stats do
    try do
      %{
        users_total: Repo.aggregate(from(u in "users"), :count),
        jobs_total: Repo.aggregate(from(j in "jobs"), :count),
        bids_total: Repo.aggregate(from(b in "bids"), :count),
        messages_total: Repo.aggregate(from(m in "messages"), :count)
      }
    rescue
      _ ->
        %{users_total: 0, jobs_total: 0, bids_total: 0, messages_total: 0}
    end
  end

  defp ai_stats do
    %{
      cost_tracker: CostTracker.daily_stats(),
      fairprice_cache: FairPrice.count(),
      fairscope_cache: FairScope.count()
    }
  end

  defp oban_stats do
    try do
      queues =
        Repo.all(
          from(j in "oban_jobs",
            where: j.state in ["available", "executing", "retryable", "discarded"],
            group_by: [j.queue, j.state],
            select: {j.queue, j.state, count(j.id)}
          )
        )
        |> Enum.group_by(fn {queue, _, _} -> queue end, fn {_, state, count} -> {state, count} end)
        |> Enum.map(fn {queue, states} -> {queue, Map.new(states)} end)
        |> Map.new()

      %{queues: queues, healthy: true}
    rescue
      _ -> %{queues: %{}, healthy: false}
    end
  end

  defp trust_stats do
    try do
      %{
        pending_verifications: length(FairTrust.pending_verifications()),
        open_flags: length(FairTrust.open_flags())
      }
    rescue
      _ -> %{pending_verifications: 0, open_flags: 0}
    end
  end

  defp ledger_stats do
    try do
      %{
        open_disputes: length(FairLedger.open_disputes()),
        recent_transactions: length(FairLedger.recent_transactions(10)),
        revenue: FairLedger.revenue_summary(30),
        forecast: FairLedger.revenue_forecast(1)
      }
    rescue
      _ ->
        %{
          open_disputes: 0,
          recent_transactions: 0,
          revenue: %{total_cents: 0, transaction_count: 0, period_days: 30, by_type: %{}},
          forecast: %{monthly_forecast_cents: 0, monthly_signups: 0, conversion_rate: 0.0}
        }
    end
  end

  defp uptime do
    {uptime_ms, _} = :erlang.statistics(:wall_clock)
    hours = div(uptime_ms, 3_600_000)
    minutes = div(rem(uptime_ms, 3_600_000), 60_000)
    "#{hours}h #{minutes}m"
  end

  @impl true
  def render(assigns) do
    ~H"""
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, sans-serif; max-width: 1200px; margin: 0 auto; padding: 24px; background: #FDFBF8; min-height: 100vh;">
      <%!-- Header --%>
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 32px;">
        <div>
          <h1 style="font-size: 24px; font-weight: 700; color: #0F1419; margin: 0;">FairCommand</h1>
          <p style="font-size: 13px; color: #9CA3AF; margin: 4px 0 0 0;">
            FairTradeWorker Admin &middot; Last refresh: {@updated_at}
          </p>
        </div>
        <span style="font-size: 11px; padding: 4px 12px; border-radius: 9999px; background: #F3F1ED; color: #4B5563; font-weight: 600;">
          Internal
        </span>
      </div>

      <%!-- Flash --%>
      <div
        :if={info = Phoenix.Flash.get(@flash, :info)}
        style="margin-bottom: 16px; padding: 12px 16px; border-radius: 12px; background: #F0FDF4; border: 1px solid #BBF7D0; color: #166534; font-size: 13px; font-weight: 500;"
      >
        {info}
      </div>

      <%!-- System Health --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          System Health
        </h2>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;">
          <.stat_card label="Uptime" value={@system.uptime} />
          <.stat_card label="Memory" value={"#{@system.total_memory_mb} MB"} />
          <.stat_card label="Processes" value={@system.process_count} />
          <.stat_card label="Node" value={@system.node |> to_string() |> String.slice(0..20)} />
        </div>
      </section>

      <%!-- RunPod Status --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          RunPod &middot; ConstructionAI
        </h2>
        <div
          :if={@runpod.connected}
          style="display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;"
        >
          <.stat_card
            label="Workers Idle"
            value={deep_get(@runpod, [:workers, "idle"]) || 0}
            color="green"
          />
          <.stat_card
            label="Workers Running"
            value={deep_get(@runpod, [:workers, "running"]) || 0}
            color="brand"
          />
          <.stat_card label="Queue Depth" value={deep_get(@runpod, [:jobs, "in_queue"]) || 0} />
          <.stat_card label="Jobs Completed" value={deep_get(@runpod, [:jobs, "completed"]) || 0} />
          <.stat_card
            label="Jobs Failed"
            value={deep_get(@runpod, [:jobs, "failed"]) || 0}
            color={if (deep_get(@runpod, [:jobs, "failed"]) || 0) > 0, do: "red", else: nil}
          />
        </div>
        <div
          :if={!@runpod.connected}
          style="padding: 16px; border-radius: 12px; background: #FEF2F2; border: 1px solid #FECACA; color: #991B1B; font-size: 13px;"
        >
          RunPod not connected — set RUNPOD_API_KEY and RUNPOD_ENDPOINT_ID env vars. Error: {@runpod.error
          |> inspect()}
        </div>
      </section>

      <%!-- AI Gateway --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          FairGate &middot; AI Gateway
        </h2>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;">
          <.stat_card label="FairPrice Cache" value={"#{@ai.fairprice_cache} entries"} color="green" />
          <.stat_card label="FairScope Cache" value={"#{@ai.fairscope_cache} entries"} />
          <.stat_card
            label="Today's Inferences"
            value={Enum.reduce(@ai.cost_tracker, 0, fn {_k, v}, acc -> acc + v.inferences end)}
          />
          <.stat_card
            label="Today's Cost"
            value={
              total =
                Enum.reduce(@ai.cost_tracker, 0.0, fn {_k, v}, acc -> acc + v.estimated_cost_cents end)

              "$#{Float.round(total / 100, 3)}"
            }
            color="brand"
          />
        </div>

        <%!-- Per-feature breakdown --%>
        <div style="margin-top: 12px; background: white; border: 1px solid #E5E1DB; border-radius: 12px; overflow: hidden;">
          <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
            <thead>
              <tr style="background: #F9F6F1; border-bottom: 1px solid #E5E1DB;">
                <th style="text-align: left; padding: 10px 16px; font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em;">
                  Feature
                </th>
                <th style="text-align: right; padding: 10px 16px; font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase;">
                  Cache Hits
                </th>
                <th style="text-align: right; padding: 10px 16px; font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase;">
                  Inferences
                </th>
                <th style="text-align: right; padding: 10px 16px; font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase;">
                  Hit Rate
                </th>
                <th style="text-align: right; padding: 10px 16px; font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase;">
                  Est. Cost
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                :for={{feature, stats} <- @ai.cost_tracker}
                style="border-bottom: 1px solid #F3F1ED;"
              >
                <td style="padding: 10px 16px; font-weight: 600; color: #0F1419;">{feature}</td>
                <td style="text-align: right; padding: 10px 16px; color: #4B5563; font-variant-numeric: tabular-nums;">
                  {stats.cache_hits}
                </td>
                <td style="text-align: right; padding: 10px 16px; color: #4B5563; font-variant-numeric: tabular-nums;">
                  {stats.inferences}
                </td>
                <td style="text-align: right; padding: 10px 16px; color: #4B5563; font-variant-numeric: tabular-nums;">
                  {if stats.total_requests > 0,
                    do: "#{round(stats.cache_hits / stats.total_requests * 100)}%",
                    else: "—"}
                </td>
                <td style="text-align: right; padding: 10px 16px; font-weight: 600; color: #0F1419; font-variant-numeric: tabular-nums;">
                  ${Float.round(stats.estimated_cost_cents / 100, 3)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <%!-- Business Pulse --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          Business Pulse
        </h2>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;">
          <.stat_card label="Users" value={@business.users_total} />
          <.stat_card label="Jobs" value={@business.jobs_total} />
          <.stat_card label="Bids" value={@business.bids_total} />
          <.stat_card label="Messages" value={@business.messages_total} />
        </div>
      </section>

      <%!-- FairTrust — Verification + Moderation --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          FairTrust — Verification + Moderation
        </h2>
        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;">
          <.stat_card
            label="Pending Verifications"
            value={@trust.pending_verifications}
            color={if @trust.pending_verifications > 0, do: "brand", else: nil}
          />
          <.stat_card
            label="Open Content Flags"
            value={@trust.open_flags}
            color={if @trust.open_flags > 0, do: "red", else: nil}
          />
        </div>
      </section>

      <%!-- FairLedger — Revenue + Disputes --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          FairLedger — Revenue + Disputes
        </h2>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;">
          <.stat_card
            label="Revenue (30d)"
            value={"$#{Float.round(@ledger.revenue.total_cents / 100, 2)}"}
            color="green"
          />
          <.stat_card
            label="Forecast (next month)"
            value={"$#{Float.round(@ledger.forecast.monthly_forecast_cents / 100, 2)}"}
            color="brand"
          />
          <.stat_card
            label="Transactions (30d)"
            value={@ledger.revenue.transaction_count}
          />
          <.stat_card
            label="Open Disputes"
            value={@ledger.open_disputes}
            color={if @ledger.open_disputes > 0, do: "red", else: nil}
          />
        </div>
      </section>

      <%!-- Oban Queues --%>
      <section style="margin-bottom: 24px;">
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          Oban Job Queues
        </h2>
        <div
          :if={@oban.healthy and map_size(@oban.queues) > 0}
          style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;"
        >
          <div
            :for={{queue, states} <- @oban.queues}
            style="background: white; border: 1px solid #E5E1DB; border-radius: 12px; padding: 16px;"
          >
            <p style="font-size: 11px; color: #9CA3AF; font-weight: 600; text-transform: uppercase; margin: 0 0 6px 0;">
              {queue}
            </p>
            <div
              :for={{state, count} <- states}
              style="display: flex; justify-content: space-between; font-size: 13px; margin-top: 4px;"
            >
              <span style="color: #4B5563;">{state}</span>
              <span style={"font-weight: 600; color: #{if state in ["discarded", "retryable"], do: "#DC2626", else: "#0F1419"}; font-variant-numeric: tabular-nums;"}>
                {count}
              </span>
            </div>
          </div>
        </div>
        <p
          :if={!@oban.healthy or map_size(@oban.queues) == 0}
          style="font-size: 13px; color: #9CA3AF; padding: 16px;"
        >
          All queues clear.
        </p>
      </section>

      <%!-- Quick Actions --%>
      <section>
        <h2 style="font-size: 11px; font-weight: 700; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.05em; margin: 0 0 12px 0;">
          Quick Actions
        </h2>
        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
          <button
            phx-click="refresh_fairprice"
            style="padding: 8px 16px; border-radius: 8px; border: 1px solid #E5E1DB; background: white; font-size: 13px; font-weight: 600; color: #0F1419; cursor: pointer;"
          >
            Refresh FairPrice Cache
          </button>
          <button
            phx-click="cleanup_fairscope"
            style="padding: 8px 16px; border-radius: 8px; border: 1px solid #E5E1DB; background: white; font-size: 13px; font-weight: 600; color: #0F1419; cursor: pointer;"
          >
            Cleanup FairScope
          </button>
          <button
            phx-click="reset_cost_tracker"
            style="padding: 8px 16px; border-radius: 8px; border: 1px solid #E5E1DB; background: white; font-size: 13px; font-weight: 600; color: #0F1419; cursor: pointer;"
          >
            Reset Cost Tracker
          </button>
        </div>
      </section>
    </div>
    """
  end

  defp stat_card(assigns) do
    color_style =
      case assigns[:color] do
        "green" -> "color: #166534;"
        "red" -> "color: #DC2626;"
        "brand" -> "color: #C41E3A;"
        _ -> "color: #0F1419;"
      end

    assigns = assign(assigns, :color_style, color_style)

    ~H"""
    <div style="background: white; border: 1px solid #E5E1DB; border-radius: 12px; padding: 16px;">
      <p style="font-size: 11px; color: #9CA3AF; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; margin: 0 0 6px 0;">
        {@label}
      </p>
      <p style={"font-size: 20px; font-weight: 700; margin: 0; font-variant-numeric: tabular-nums; #{@color_style}"}>
        {@value}
      </p>
    </div>
    """
  end

  defp deep_get(map, keys) do
    Enum.reduce(keys, map, fn key, acc ->
      case acc do
        %{^key => val} -> val
        _ -> nil
      end
    end)
  end
end
