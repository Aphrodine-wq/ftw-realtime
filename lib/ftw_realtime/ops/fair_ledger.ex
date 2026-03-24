defmodule FtwRealtime.Ops.FairLedger do
  @moduledoc """
  Financial tracking, dispute management, and revenue forecasting.

  Immutable transaction log, dispute lifecycle, and revenue metrics.
  """

  alias FtwRealtime.Repo

  alias FtwRealtime.Marketplace.{
    TransactionLog,
    Dispute,
    DisputeEvidence,
    RevenueSnapshot,
    Job,
    User
  }

  import Ecto.Query

  # ── Transaction Logging ─────────────────────────────────────────────

  @doc "Log a financial event. Immutable append-only."
  def log_transaction(type, attrs) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    %TransactionLog{}
    |> TransactionLog.changeset(Map.merge(attrs, %{type: type, recorded_at: now}))
    |> Repo.insert()
  end

  @doc "Recent transactions for admin dashboard."
  def recent_transactions(limit \\ 20) do
    from(t in TransactionLog,
      order_by: [desc: t.recorded_at],
      limit: ^limit
    )
    |> Repo.all()
  end

  @doc "All transactions for a contractor."
  def contractor_payment_history(contractor_id) do
    from(t in TransactionLog,
      where: t.contractor_id == ^contractor_id,
      order_by: [desc: t.recorded_at]
    )
    |> Repo.all()
  end

  # ── Disputes ────────────────────────────────────────────────────────

  @doc "Open a dispute on a job."
  def open_dispute(job_id, user_id, reason, description) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    job = Repo.get!(Job, job_id)

    # Determine contractor and homeowner from job
    # The job has homeowner_id; we need to find the accepted bid's contractor
    contractor_id =
      from(b in FtwRealtime.Marketplace.Bid,
        where: b.job_id == ^job_id and b.status == :accepted,
        select: b.contractor_id,
        limit: 1
      )
      |> Repo.one()

    if is_nil(contractor_id) do
      {:error, :no_accepted_bid}
    else
      Repo.transaction(fn ->
        # Create dispute
        {:ok, dispute} =
          %Dispute{}
          |> Dispute.changeset(%{
            job_id: job_id,
            opened_by: user_id,
            contractor_id: contractor_id,
            homeowner_id: job.homeowner_id,
            reason: reason,
            description: description,
            opened_at: now
          })
          |> Repo.insert()

        # Transition job to disputed
        FtwRealtime.Marketplace.transition_job(job_id, "disputed", user_id)

        dispute
      end)
    end
  end

  @doc "Add evidence to a dispute."
  def add_evidence(dispute_id, user_id, type, content) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    %DisputeEvidence{}
    |> DisputeEvidence.changeset(%{
      dispute_id: dispute_id,
      submitted_by: user_id,
      type: type,
      content: content,
      submitted_at: now
    })
    |> Repo.insert()
  end

  @doc "Resolve a dispute."
  def resolve_dispute(dispute_id, resolution, admin_id, notes \\ nil) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    case Repo.get(Dispute, dispute_id) do
      nil ->
        {:error, :not_found}

      dispute ->
        dispute
        |> Dispute.changeset(%{
          status: resolution,
          resolved_by: admin_id,
          resolved_at: now,
          resolution_notes: notes
        })
        |> Repo.update()
    end
  end

  @doc "List open disputes for admin."
  def open_disputes do
    from(d in Dispute,
      where: d.status in ["open", "investigating", "escalated"],
      order_by: [asc: d.opened_at],
      preload: [:evidence]
    )
    |> Repo.all()
  end

  @doc "Get dispute with all evidence."
  def dispute_detail(dispute_id) do
    Repo.get(Dispute, dispute_id)
    |> Repo.preload([:evidence])
  end

  # ── Revenue ─────────────────────────────────────────────────────────

  @doc "Revenue summary for a date range."
  def revenue_summary(days_back \\ 30) do
    since = Date.utc_today() |> Date.add(-days_back)

    transactions =
      from(t in TransactionLog,
        where: fragment("?::date", t.recorded_at) >= ^since,
        select: %{type: t.type, amount: t.amount}
      )
      |> Repo.all()

    total = Enum.reduce(transactions, 0, fn t, acc -> acc + t.amount end)

    by_type =
      transactions
      |> Enum.group_by(& &1.type)
      |> Enum.map(fn {type, txns} ->
        {type, Enum.reduce(txns, 0, fn t, a -> a + t.amount end)}
      end)
      |> Map.new()

    %{
      total_cents: total,
      by_type: by_type,
      transaction_count: length(transactions),
      period_days: days_back
    }
  end

  @doc """
  Revenue forecast based on recent activity.

  forecast = monthly_signups * conversion_rate * avg_transaction * commission_rate
  """
  def revenue_forecast(months_ahead \\ 3) do
    now = Date.utc_today()
    ninety_days_ago = Date.add(now, -90)

    # Monthly signup rate (last 90 days)
    signup_count =
      Repo.aggregate(
        from(u in User, where: fragment("?::date", u.inserted_at) >= ^ninety_days_ago),
        :count
      ) || 0

    monthly_signups = max(1, div(signup_count, 3))

    # Conversion rate: completed jobs / total jobs (last 90 days)
    total_jobs =
      Repo.aggregate(
        from(j in Job, where: fragment("?::date", j.inserted_at) >= ^ninety_days_ago),
        :count
      ) || 0

    completed_jobs =
      Repo.aggregate(
        from(j in Job,
          where: fragment("?::date", j.inserted_at) >= ^ninety_days_ago and j.status == :completed
        ),
        :count
      ) || 0

    conversion_rate = if total_jobs > 0, do: completed_jobs / total_jobs, else: 0.1

    # Average transaction value (accepted bids)
    avg_bid =
      Repo.one(
        from(b in FtwRealtime.Marketplace.Bid,
          where: fragment("?::date", b.inserted_at) >= ^ninety_days_ago and b.status == :accepted,
          select: avg(b.amount)
        )
      )

    avg_transaction = if avg_bid, do: Decimal.to_float(avg_bid), else: 25_000

    commission_rate = 0.02

    monthly_forecast =
      round(monthly_signups * conversion_rate * avg_transaction * commission_rate)

    %{
      monthly_signups: monthly_signups,
      conversion_rate: Float.round(conversion_rate, 3),
      avg_transaction_cents: round(avg_transaction),
      commission_rate: commission_rate,
      monthly_forecast_cents: monthly_forecast,
      months:
        Enum.map(1..months_ahead, fn m ->
          %{
            month: Date.add(now, m * 30) |> Calendar.strftime("%B %Y"),
            forecast_cents: monthly_forecast * m
          }
        end)
    }
  end

  @doc "Compute and store a daily revenue snapshot."
  def take_snapshot(date \\ Date.utc_today()) do
    day_start = DateTime.new!(date, ~T[00:00:00], "Etc/UTC")
    day_end = DateTime.new!(Date.add(date, 1), ~T[00:00:00], "Etc/UTC")

    revenue =
      Repo.aggregate(
        from(t in TransactionLog,
          where: t.recorded_at >= ^day_start and t.recorded_at < ^day_end
        ),
        :sum,
        :amount
      ) || 0

    jobs =
      Repo.aggregate(
        from(j in Job,
          where: fragment("?::date", j.updated_at) == ^date and j.status == :completed
        ),
        :count
      ) || 0

    bids =
      Repo.aggregate(
        from(b in FtwRealtime.Marketplace.Bid,
          where: fragment("?::date", b.inserted_at) == ^date
        ),
        :count
      ) || 0

    users =
      Repo.aggregate(
        from(u in User, where: fragment("?::date", u.inserted_at) == ^date),
        :count
      ) || 0

    disputes =
      Repo.aggregate(
        from(d in Dispute, where: fragment("?::date", d.opened_at) == ^date),
        :count
      ) || 0

    now = DateTime.utc_now() |> DateTime.truncate(:second)

    %RevenueSnapshot{}
    |> RevenueSnapshot.changeset(%{
      date: date,
      total_revenue: revenue,
      jobs_completed: jobs,
      bids_placed: bids,
      users_signed_up: users,
      disputes_opened: disputes
    })
    |> Map.put(:inserted_at, now)
    |> Map.put(:updated_at, now)
    |> Repo.insert(
      on_conflict:
        {:replace,
         [
           :total_revenue,
           :jobs_completed,
           :bids_placed,
           :users_signed_up,
           :disputes_opened,
           :updated_at
         ]},
      conflict_target: [:date]
    )
  end
end
