defmodule FtwRealtimeWeb.Api.VerificationController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Ops.FairTrust

  @doc "Get verification status for current contractor."
  def status(conn, _params) do
    contractor_id = conn.assigns.current_user_id
    verification = FairTrust.verification_status(contractor_id)
    scorecard = FairTrust.contractor_scorecard(contractor_id)

    json(conn, %{
      verification: %{
        steps: serialize_steps(verification.steps),
        fully_verified: verification.fully_verified,
        pending_count: verification.pending_count,
        approved_count: verification.approved_count,
        total_steps: verification.total_steps
      },
      scorecard: scorecard
    })
  end

  @doc "Submit a verification step (license, insurance, background, identity)."
  def submit(conn, %{"step" => step} = params) do
    contractor_id = conn.assigns.current_user_id
    data = Map.get(params, "data", %{})

    case FairTrust.submit_verification(contractor_id, step, data) do
      {:ok, verification} ->
        conn
        |> put_status(:created)
        |> json(%{verification: serialize_verification(verification)})

      {:error, changeset} ->
        conn
        |> put_status(:unprocessable_entity)
        |> json(%{errors: format_errors(changeset)})
    end
  end

  @doc "Webhook handler for Persona identity verification callbacks."
  def persona_webhook(conn, params) do
    event_type = params["event_type"]
    data = params["data"] || %{}

    case event_type do
      "inquiry.completed" ->
        handle_persona_inquiry(data)
        json(conn, %{ok: true})

      "inquiry.failed" ->
        handle_persona_failure(data)
        json(conn, %{ok: true})

      _ ->
        json(conn, %{ok: true})
    end
  end

  @doc "Webhook handler for Checkr background check callbacks."
  def checkr_webhook(conn, params) do
    event_type = params["type"]
    data = params["data"] || %{}

    case event_type do
      "report.completed" ->
        handle_checkr_report(data)
        json(conn, %{ok: true})

      "candidate.created" ->
        json(conn, %{ok: true})

      _ ->
        json(conn, %{ok: true})
    end
  end

  # --- Persona handlers ---

  defp handle_persona_inquiry(data) do
    reference_id = get_in(data, ["attributes", "reference_id"])
    status = get_in(data, ["attributes", "status"])

    if reference_id do
      case status do
        "completed" ->
          FairTrust.submit_verification(reference_id, "identity", %{
            "provider" => "persona",
            "inquiry_id" => data["id"],
            "status" => "completed"
          })
          |> maybe_auto_approve("identity")

        _ ->
          :ok
      end
    end
  end

  defp handle_persona_failure(data) do
    reference_id = get_in(data, ["attributes", "reference_id"])

    if reference_id do
      FairTrust.submit_verification(reference_id, "identity", %{
        "provider" => "persona",
        "inquiry_id" => data["id"],
        "status" => "failed"
      })
    end
  end

  # --- Checkr handlers ---

  defp handle_checkr_report(data) do
    candidate_id = get_in(data, ["object", "candidate_id"])
    report_status = get_in(data, ["object", "status"])

    # Look up contractor by checkr candidate_id stored in verification data
    case find_contractor_by_checkr_candidate(candidate_id) do
      nil ->
        :ok

      contractor_id ->
        FairTrust.submit_verification(contractor_id, "background", %{
          "provider" => "checkr",
          "report_id" => get_in(data, ["object", "id"]),
          "status" => report_status
        })
        |> maybe_auto_approve_background(report_status)
    end
  end

  defp find_contractor_by_checkr_candidate(candidate_id) when is_binary(candidate_id) do
    import Ecto.Query

    alias FtwRealtime.Repo
    alias FtwRealtime.Marketplace.Verification

    case Repo.one(
           from(v in Verification,
             where: v.step == "background",
             where: fragment("?->>'checkr_candidate_id' = ?", v.data, ^candidate_id),
             select: v.contractor_id,
             limit: 1
           )
         ) do
      nil -> nil
      contractor_id -> contractor_id
    end
  end

  defp find_contractor_by_checkr_candidate(_), do: nil

  defp maybe_auto_approve({:ok, verification}, step) do
    # Auto-approve identity verification if Persona says completed
    if step == "identity" do
      FairTrust.approve_verification(verification.id, "system",
        expires_at: DateTime.add(DateTime.utc_now(), 365 * 24 * 3600, :second)
      )
    end
  end

  defp maybe_auto_approve(_, _), do: :ok

  defp maybe_auto_approve_background({:ok, verification}, "clear") do
    FairTrust.approve_verification(verification.id, "system",
      expires_at: DateTime.add(DateTime.utc_now(), 365 * 24 * 3600, :second)
    )
  end

  defp maybe_auto_approve_background(_, _), do: :ok

  # --- Serializers ---

  defp serialize_steps(steps) do
    Map.new(steps, fn {step, verification} ->
      {step, serialize_verification(verification)}
    end)
  end

  defp serialize_verification(%{} = v) do
    %{
      id: v.id,
      step: v.step,
      status: v.status,
      data: v.data,
      reviewed_at: v.reviewed_at,
      expires_at: v.expires_at,
      submitted_at: v.inserted_at
    }
  end

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
