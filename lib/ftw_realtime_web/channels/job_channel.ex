defmodule FtwRealtimeWeb.JobChannel do
  @moduledoc """
  Real-time job feed. Clients join "jobs:feed" to see new postings appear live.
  """
  use Phoenix.Channel

  alias FtwRealtime.Marketplace

  @impl true
  def join("jobs:feed", _params, socket) do
    send(self(), :after_join)
    {:ok, socket}
  end

  @impl true
  def handle_info(:after_join, socket) do
    jobs = Marketplace.list_jobs()
    push(socket, "jobs:list", %{jobs: jobs})
    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "jobs")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"job:posted", job}, socket) do
    push(socket, "job:posted", job)
    {:noreply, socket}
  end

  @impl true
  def handle_info({"job:updated", job}, socket) do
    push(socket, "job:updated", job)
    {:noreply, socket}
  end

  @impl true
  def handle_in("post_job", attrs, socket) do
    case Marketplace.post_job(attrs) do
      {:ok, job} -> {:reply, {:ok, job}, socket}
      {:error, reason} -> {:reply, {:error, %{reason: reason}}, socket}
    end
  end
end
