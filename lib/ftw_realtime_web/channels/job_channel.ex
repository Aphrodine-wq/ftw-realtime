defmodule FtwRealtimeWeb.JobChannel do
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
    push(socket, "jobs:list", %{jobs: Enum.map(jobs, &serialize_job/1)})
    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "jobs")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"job:posted", job_data}, socket) do
    push(socket, "job:posted", job_data)
    {:noreply, socket}
  end

  @impl true
  def handle_info({"job:updated", job_data}, socket) do
    push(socket, "job:updated", job_data)
    {:noreply, socket}
  end

  @impl true
  def handle_in("post_job", attrs, socket) do
    attrs = Map.put(attrs, "homeowner_id", socket.assigns.user_id)

    case Marketplace.post_job(attrs) do
      {:ok, job} -> {:reply, {:ok, serialize_job(job)}, socket}
      {:error, changeset} -> {:reply, {:error, %{errors: format_errors(changeset)}}, socket}
    end
  end

  defp serialize_job(%{id: _} = job) do
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
      posted_at: job.inserted_at
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
