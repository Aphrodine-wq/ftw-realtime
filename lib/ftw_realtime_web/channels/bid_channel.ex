defmodule FtwRealtimeWeb.BidChannel do
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

    push(socket, "job:details", %{
      job: serialize_job(job),
      bids: Enum.map(bids, &serialize_bid/1)
    })

    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "job:#{job_id}")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"bid:placed", bid_data}, socket) do
    push(socket, "bid:placed", bid_data)
    {:noreply, socket}
  end

  @impl true
  def handle_info({"bid:accepted", bid_data}, socket) do
    push(socket, "bid:accepted", bid_data)
    {:noreply, socket}
  end

  @impl true
  def handle_in("place_bid", attrs, socket) do
    job_id = socket.assigns.job_id
    attrs = Map.put(attrs, "contractor_id", socket.assigns.user_id)

    case Marketplace.place_bid(job_id, attrs) do
      {:ok, bid} -> {:reply, {:ok, serialize_bid(bid)}, socket}
      {:error, :job_not_found} -> {:reply, {:error, %{reason: "job not found"}}, socket}
      {:error, changeset} -> {:reply, {:error, %{errors: format_errors(changeset)}}, socket}
    end
  end

  @impl true
  def handle_in("accept_bid", %{"bid_id" => bid_id}, socket) do
    job_id = socket.assigns.job_id

    case Marketplace.accept_bid(job_id, bid_id) do
      {:ok, bid} -> {:reply, {:ok, serialize_bid(bid)}, socket}
      {:error, :bid_not_found} -> {:reply, {:error, %{reason: "bid not found"}}, socket}
      {:error, reason} -> {:reply, {:error, %{reason: reason}}, socket}
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
      placed_at: bid.inserted_at
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
