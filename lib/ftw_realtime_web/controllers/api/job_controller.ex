defmodule FtwRealtimeWeb.Api.JobController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, _params) do
    jobs = Marketplace.list_jobs()
    json(conn, %{jobs: jobs})
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_job(id) do
      nil -> conn |> put_status(:not_found) |> json(%{error: "Job not found"})
      job -> json(conn, %{job: job, bids: Marketplace.list_bids(id)})
    end
  end

  def create(conn, %{"job" => job_params}) do
    case Marketplace.post_job(job_params) do
      {:ok, job} -> conn |> put_status(:created) |> json(%{job: job})
      {:error, reason} -> conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  end

  def place_bid(conn, %{"id" => job_id, "bid" => bid_params}) do
    case Marketplace.place_bid(job_id, bid_params) do
      {:ok, bid} -> conn |> put_status(:created) |> json(%{bid: bid})
      {:error, reason} -> conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  end

  def accept_bid(conn, %{"id" => job_id, "bid_id" => bid_id}) do
    case Marketplace.accept_bid(job_id, bid_id) do
      {:ok, bid} -> json(conn, %{bid: bid})
      {:error, reason} -> conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  end
end
