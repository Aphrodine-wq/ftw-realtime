defmodule FtwRealtimeWeb.Api.FairRecordController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  # Public — anyone can verify a FairRecord by public_id
  def verify(conn, %{"public_id" => public_id}) do
    case Marketplace.get_fair_record_by_public_id(public_id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Record not found"})

      record ->
        json(conn, %{record: serialize_public(record)})
    end
  end

  # Authenticated — list all records for a contractor
  def contractor_index(conn, %{"contractor_id" => contractor_id}) do
    records = Marketplace.list_contractor_records(contractor_id)
    stats = Marketplace.contractor_record_stats(contractor_id)

    json(conn, %{
      records: Enum.map(records, &serialize_record/1),
      stats: stats
    })
  end

  # Authenticated — get the FairRecord for a specific project
  def project_record(conn, %{"project_id" => project_id}) do
    case Marketplace.get_fair_record_by_project(project_id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "No record for this project"})

      record ->
        json(conn, %{record: serialize_record(record)})
    end
  end

  # Authenticated — homeowner confirms project completion
  def confirm(conn, %{"record_id" => record_id}) do
    homeowner_id = conn.assigns.current_user_id

    case Marketplace.confirm_fair_record(record_id, homeowner_id) do
      {:ok, record} ->
        json(conn, %{record: serialize_record(record)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Record not found"})

      {:error, :unauthorized} ->
        conn |> put_status(:forbidden) |> json(%{error: "Not authorized"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  # Public — printable HTML certificate for a FairRecord
  def certificate(conn, %{"public_id" => public_id}) do
    case Marketplace.get_fair_record_by_public_id(public_id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Record not found"})

      record ->
        {:ok, html} = FtwRealtime.FairRecordPdf.generate_html(record)

        conn
        |> put_resp_content_type("text/html")
        |> send_resp(200, html)
    end
  end

  # --- Serializers ---

  defp serialize_public(record) do
    %{
      public_id: record.public_id,
      category: record.category,
      location_city: record.location_city,
      scope_summary: record.scope_summary,
      estimated_budget: record.estimated_budget,
      final_cost: record.final_cost,
      budget_accuracy_pct: record.budget_accuracy_pct,
      on_budget: record.on_budget,
      estimated_end_date: record.estimated_end_date,
      actual_completion_date: record.actual_completion_date,
      on_time: record.on_time,
      avg_rating: record.avg_rating,
      review_count: record.review_count,
      dispute_count: record.dispute_count,
      photos: record.photos,
      homeowner_confirmed: record.homeowner_confirmed,
      confirmed_at: record.confirmed_at,
      contractor: serialize_user(record.contractor),
      verified: record.homeowner_confirmed,
      created_at: record.inserted_at
    }
  end

  defp serialize_record(record) do
    %{
      id: record.id,
      public_id: record.public_id,
      project_id: record.project_id,
      contractor_id: record.contractor_id,
      homeowner_id: record.homeowner_id,
      category: record.category,
      location_city: record.location_city,
      scope_summary: record.scope_summary,
      estimated_budget: record.estimated_budget,
      final_cost: record.final_cost,
      budget_accuracy_pct: record.budget_accuracy_pct,
      on_budget: record.on_budget,
      estimated_end_date: record.estimated_end_date,
      actual_completion_date: record.actual_completion_date,
      on_time: record.on_time,
      quality_score_at_completion: record.quality_score_at_completion,
      avg_rating: record.avg_rating,
      review_count: record.review_count,
      dispute_count: record.dispute_count,
      photos: record.photos,
      homeowner_confirmed: record.homeowner_confirmed,
      confirmed_at: record.confirmed_at,
      signature_hash: record.signature_hash,
      created_at: record.inserted_at,
      updated_at: record.updated_at
    }
  end

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name, rating: user.rating, jobs_completed: user.jobs_completed}
  end

  defp serialize_user(_), do: nil

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
