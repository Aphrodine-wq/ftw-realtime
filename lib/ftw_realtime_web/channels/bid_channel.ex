defmodule FtwRealtimeWeb.BidChannel do
  @moduledoc """
  Live bidding on a specific job. Clients join "job:<job_id>" to watch bids come in.
  """
  use Phoenix.Channel

  alias FtwRealtime.Marketplace

  @impl true
  def join("job:" <> job_id, _params, socket) do
    case Marketplace.get_job(job_id) do
      nil ->
        {:error, %{reason: "job not found"}}

      _job ->
        send(self(), {:after_join, job_id})
        {:ok, assign(socket, :job_id, job_id)}
    end
  end

  @impl true
  def handle_info({:after_join, job_id}, socket) do
    job = Marketplace.get_job(job_id)
    bids = Marketplace.list_bids(job_id)
    push(socket, "job:details", %{job: job, bids: bids})
    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "job:#{job_id}")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"bid:placed", bid}, socket) do
    push(socket, "bid:placed", bid)
    {:noreply, socket}
  end

  @impl true
  def handle_info({"bid:accepted", bid}, socket) do
    push(socket, "bid:accepted", bid)
    {:noreply, socket}
  end

  @impl true
  def handle_in("place_bid", attrs, socket) do
    job_id = socket.assigns.job_id

    case Marketplace.place_bid(job_id, attrs) do
      {:ok, bid} -> {:reply, {:ok, bid}, socket}
      {:error, reason} -> {:reply, {:error, %{reason: reason}}, socket}
    end
  end

  @impl true
  def handle_in("accept_bid", %{"bid_id" => bid_id}, socket) do
    job_id = socket.assigns.job_id

    case Marketplace.accept_bid(job_id, bid_id) do
      {:ok, bid} -> {:reply, {:ok, bid}, socket}
      {:error, reason} -> {:reply, {:error, %{reason: reason}}, socket}
    end
  end
end
