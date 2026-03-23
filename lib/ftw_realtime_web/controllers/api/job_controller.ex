defmodule FtwRealtimeWeb.Api.JobController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, params) do
    limit = case Integer.parse(params["limit"] || "") do
      {n, _} when n > 0 -> n
      _ -> 20
    end

    opts = [
      status: params["status"],
      category: params["category"],
      limit: limit,
      after: params["after"]
    ]

    %{jobs: jobs, has_more: has_more, next_cursor: next_cursor} = Marketplace.list_jobs(opts)

    json(conn, %{
      jobs: Enum.map(jobs, &serialize_job/1),
      has_more: has_more,
      next_cursor: next_cursor
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_job(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Job not found"})

      job ->
        bids = Marketplace.list_bids(id)

        json(conn, %{
          job: serialize_job(job),
          bids: Enum.map(bids, &serialize_bid/1)
        })
    end
  end

  def create(conn, %{"job" => job_params}) do
    job_params = Map.put(job_params, "homeowner_id", conn.assigns.current_user_id)

    case Marketplace.post_job(job_params) do
      {:ok, job} ->
        conn |> put_status(:created) |> json(%{job: serialize_job(job)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def place_bid(conn, %{"id" => job_id, "bid" => bid_params}) do
    bid_params = Map.put(bid_params, "contractor_id", conn.assigns.current_user_id)

    case Marketplace.place_bid(job_id, bid_params) do
      {:ok, bid} ->
        conn |> put_status(:created) |> json(%{bid: serialize_bid(bid)})

      {:error, :job_not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Job not found"})

      {:error, :job_not_accepting_bids} ->
        conn |> put_status(:conflict) |> json(%{error: "Job is not accepting bids"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def transition(conn, %{"id" => job_id, "status" => status_str}) do
    status = String.to_existing_atom(status_str)

    case Marketplace.transition_job(job_id, status, conn.assigns.current_user_id) do
      {:ok, job} ->
        json(conn, %{job: serialize_job(job)})

      {:error, {:invalid_transition, from, to}} ->
        conn
        |> put_status(:conflict)
        |> json(%{error: "Cannot transition from #{from} to #{to}"})

      {:error, :only_winning_contractor} ->
        conn |> put_status(:forbidden) |> json(%{error: "Only the winning contractor can start work"})

      {:error, :only_homeowner_can_cancel} ->
        conn |> put_status(:forbidden) |> json(%{error: "Only the homeowner can cancel"})

      {:error, reason} ->
        conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  rescue
    ArgumentError ->
      conn |> put_status(:bad_request) |> json(%{error: "Invalid status"})
  end

  def accept_bid(conn, %{"id" => job_id, "bid_id" => bid_id}) do
    case Marketplace.accept_bid(job_id, bid_id) do
      {:ok, bid} ->
        json(conn, %{bid: serialize_bid(bid)})

      {:error, :bid_not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Bid not found"})

      {:error, reason} ->
        conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  end

  defp serialize_job(job) do
    %{
      id: job.id,
      title: job.title,
      description: job.description,
      category: job.category,
      budget_min: job.budget_min,
      budget_max: job.budget_max,
      location: job.location,
      status: job.status,
      bid_count: job.bid_count,
      homeowner: serialize_user(job.homeowner),
      posted_at: job.inserted_at
    }
  end

  defp serialize_bid(bid) do
    %{
      id: bid.id,
      job_id: bid.job_id,
      amount: bid.amount,
      message: bid.message,
      timeline: bid.timeline,
      status: bid.status,
      contractor: serialize_user(bid.contractor),
      placed_at: bid.inserted_at
    }
  end

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name, role: user.role}
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
