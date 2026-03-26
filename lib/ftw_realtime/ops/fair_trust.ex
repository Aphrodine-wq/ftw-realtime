defmodule FtwRealtime.Ops.FairTrust do
  @moduledoc """
  Contractor verification pipeline and quality scoring.

  Tracks verification steps (license, insurance, background, identity),
  computes quality scores, and manages content moderation flags.
  """

  alias FtwRealtime.Repo
  alias FtwRealtime.Marketplace.{User, Verification, ContentFlag, Review, FairRecord}

  import Ecto.Query

  # ── Verification ────────────────────────────────────────────────────

  @doc "Get all verification steps for a contractor with overall status."
  def verification_status(contractor_id) do
    steps = Repo.all(from(v in Verification, where: v.contractor_id == ^contractor_id))
    step_map = Map.new(steps, fn v -> {v.step, v} end)

    all_approved =
      Verification.steps()
      |> Enum.all?(fn step ->
        case Map.get(step_map, step) do
          %{status: "approved"} -> true
          _ -> false
        end
      end)

    %{
      steps: step_map,
      fully_verified: all_approved,
      pending_count: Enum.count(steps, &(&1.status == "pending")),
      approved_count: Enum.count(steps, &(&1.status == "approved")),
      total_steps: length(Verification.steps())
    }
  end

  @doc "Submit a verification step for review."
  def submit_verification(contractor_id, step, data \\ %{}) do
    %Verification{}
    |> Verification.changeset(%{
      contractor_id: contractor_id,
      step: step,
      status: "pending",
      data: data
    })
    |> Repo.insert(
      on_conflict: {:replace, [:status, :data, :updated_at]},
      conflict_target: [:contractor_id, :step]
    )
  end

  @doc "Approve a verification step."
  def approve_verification(verification_id, admin_id, opts \\ []) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)
    expires_at = Keyword.get(opts, :expires_at)

    case Repo.get(Verification, verification_id) do
      nil ->
        {:error, :not_found}

      verification ->
        verification
        |> Verification.changeset(%{
          status: "approved",
          reviewed_by: admin_id,
          reviewed_at: now,
          expires_at: expires_at
        })
        |> Repo.update()
    end
  end

  @doc "Reject a verification step."
  def reject_verification(verification_id, admin_id, notes \\ nil) do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    case Repo.get(Verification, verification_id) do
      nil ->
        {:error, :not_found}

      verification ->
        verification
        |> Verification.changeset(%{
          status: "rejected",
          reviewed_by: admin_id,
          reviewed_at: now,
          notes: notes
        })
        |> Repo.update()
    end
  end

  @doc "Find and expire verifications past their expiry date."
  def check_expirations do
    now = DateTime.utc_now()

    {count, _} =
      from(v in Verification,
        where: v.status == "approved" and not is_nil(v.expires_at) and v.expires_at < ^now
      )
      |> Repo.update_all(set: [status: "expired", updated_at: now])

    count
  end

  @doc "List pending verifications for admin review."
  def pending_verifications do
    from(v in Verification,
      where: v.status == "pending",
      join: u in User,
      on: u.id == v.contractor_id,
      select: %{
        id: v.id,
        contractor_id: v.contractor_id,
        contractor_name: u.name,
        step: v.step,
        data: v.data,
        submitted_at: v.inserted_at
      },
      order_by: [asc: v.inserted_at]
    )
    |> Repo.all()
  end

  # ── Quality Scoring ─────────────────────────────────────────────────

  @doc """
  Compute quality score for a contractor (0-100).

  Formula:
  - 40% — average review rating (out of 5)
  - 25% — job completion rate
  - 20% — on-time rate (placeholder — needs project end_date tracking)
  - 15% — budget adherence (placeholder — needs bid vs final cost tracking)
  """
  def compute_quality_score(contractor_id) do
    avg_rating = avg_review_rating(contractor_id)
    completion_rate = job_completion_rate(contractor_id)
    on_time_rate = fair_record_on_time_rate(contractor_id)
    budget_adherence = fair_record_budget_adherence(contractor_id)

    score =
      round(
        avg_rating / 5.0 * 40 +
          completion_rate * 25 +
          on_time_rate * 20 +
          budget_adherence * 15
      )

    score = max(0, min(100, score))

    # Update user record
    from(u in User, where: u.id == ^contractor_id)
    |> Repo.update_all(set: [quality_score: score])

    score
  end

  @doc "Full scorecard for a contractor."
  def contractor_scorecard(contractor_id) do
    %{
      quality_score: compute_quality_score(contractor_id),
      avg_rating: avg_review_rating(contractor_id),
      review_count: review_count(contractor_id),
      completion_rate: job_completion_rate(contractor_id),
      verification: verification_status(contractor_id)
    }
  end

  defp avg_review_rating(contractor_id) do
    case Repo.one(
           from(r in Review,
             where: r.reviewed_id == ^contractor_id,
             select: avg(r.rating)
           )
         ) do
      nil -> 0.0
      avg -> Decimal.to_float(avg)
    end
  end

  defp review_count(contractor_id) do
    Repo.aggregate(from(r in Review, where: r.reviewed_id == ^contractor_id), :count)
  end

  defp job_completion_rate(contractor_id) do
    total =
      Repo.aggregate(
        from(u in User, where: u.id == ^contractor_id, select: u.jobs_completed),
        :sum
      ) || 0

    # Approximate — awarded jobs include completed + in_progress
    if total > 0, do: min(1.0, total / max(total, 1)), else: 0.0
  end

  # ── FairRecord Metrics ──────────────────────────────────────────────

  defp fair_record_on_time_rate(contractor_id) do
    total =
      Repo.aggregate(
        from(fr in FairRecord, where: fr.contractor_id == ^contractor_id and fr.homeowner_confirmed == true),
        :count
      )

    if total == 0 do
      0.85
    else
      on_time =
        Repo.aggregate(
          from(fr in FairRecord,
            where: fr.contractor_id == ^contractor_id and fr.homeowner_confirmed == true and fr.on_time == true
          ),
          :count
        )

      on_time / total
    end
  end

  defp fair_record_budget_adherence(contractor_id) do
    avg =
      Repo.one(
        from(fr in FairRecord,
          where: fr.contractor_id == ^contractor_id and fr.homeowner_confirmed == true,
          select: avg(fr.budget_accuracy_pct)
        )
      )

    case avg do
      nil -> 0.90
      val -> Decimal.to_float(val) / 100.0
    end
  end

  # ── Content Moderation ──────────────────────────────────────────────

  @doc "Flag content for moderation."
  def flag_content(entity_type, entity_id, reason, flagged_by \\ nil) do
    %ContentFlag{}
    |> ContentFlag.changeset(%{
      entity_type: entity_type,
      entity_id: entity_id,
      reason: reason,
      flagged_by: flagged_by
    })
    |> Repo.insert()
  end

  @doc "Review a content flag (dismiss or action)."
  def review_flag(flag_id, status, admin_id) when status in ["dismissed", "actioned"] do
    now = DateTime.utc_now() |> DateTime.truncate(:second)

    case Repo.get(ContentFlag, flag_id) do
      nil ->
        {:error, :not_found}

      flag ->
        flag
        |> ContentFlag.changeset(%{status: status, resolved_by: admin_id, resolved_at: now})
        |> Repo.update()
    end
  end

  @doc "List open content flags for admin review."
  def open_flags do
    from(f in ContentFlag,
      where: f.status == "open",
      order_by: [asc: f.inserted_at],
      limit: 50
    )
    |> Repo.all()
  end
end
