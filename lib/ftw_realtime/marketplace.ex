defmodule FtwRealtime.Marketplace do
  @moduledoc """
  In-memory marketplace state. Manages jobs, bids, and contractor presence.
  Broadcasts all changes via PubSub so channels and LiveViews update instantly.
  """
  use GenServer

  alias Phoenix.PubSub

  # --- Client API ---

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, %{}, name: __MODULE__)
  end

  def list_jobs do
    GenServer.call(__MODULE__, :list_jobs)
  end

  def get_job(job_id) do
    GenServer.call(__MODULE__, {:get_job, job_id})
  end

  def post_job(attrs) do
    GenServer.call(__MODULE__, {:post_job, attrs})
  end

  def place_bid(job_id, bid_attrs) do
    GenServer.call(__MODULE__, {:place_bid, job_id, bid_attrs})
  end

  def accept_bid(job_id, bid_id) do
    GenServer.call(__MODULE__, {:accept_bid, job_id, bid_id})
  end

  def list_bids(job_id) do
    GenServer.call(__MODULE__, {:list_bids, job_id})
  end

  def send_message(conversation_id, message_attrs) do
    GenServer.call(__MODULE__, {:send_message, conversation_id, message_attrs})
  end

  def list_messages(conversation_id) do
    GenServer.call(__MODULE__, {:list_messages, conversation_id})
  end

  # --- Server Callbacks ---

  @impl true
  def init(_) do
    state = %{
      jobs: %{},
      bids: %{},
      messages: %{},
      next_id: 1
    }

    {:ok, seed_demo_data(state)}
  end

  @impl true
  def handle_call(:list_jobs, _from, state) do
    jobs =
      state.jobs
      |> Map.values()
      |> Enum.sort_by(& &1.posted_at, {:desc, DateTime})

    {:reply, jobs, state}
  end

  @impl true
  def handle_call({:get_job, job_id}, _from, state) do
    {:reply, Map.get(state.jobs, job_id), state}
  end

  @impl true
  def handle_call({:post_job, attrs}, _from, state) do
    {id, state} = next_id(state)
    job_id = "job_#{id}"

    job = %{
      id: job_id,
      title: attrs["title"] || attrs[:title],
      description: attrs["description"] || attrs[:description],
      category: attrs["category"] || attrs[:category] || "general",
      budget_min: attrs["budget_min"] || attrs[:budget_min] || 0,
      budget_max: attrs["budget_max"] || attrs[:budget_max] || 0,
      location: attrs["location"] || attrs[:location] || "Not specified",
      homeowner: attrs["homeowner"] || attrs[:homeowner] || "Anonymous",
      status: "open",
      posted_at: DateTime.utc_now(),
      bid_count: 0
    }

    state = put_in(state.jobs[job_id], job)
    broadcast("jobs", "job:posted", job)
    {:reply, {:ok, job}, state}
  end

  @impl true
  def handle_call({:place_bid, job_id, bid_attrs}, _from, state) do
    case Map.get(state.jobs, job_id) do
      nil ->
        {:reply, {:error, :job_not_found}, state}

      job ->
        {id, state} = next_id(state)
        bid_id = "bid_#{id}"

        bid = %{
          id: bid_id,
          job_id: job_id,
          contractor: bid_attrs["contractor"] || bid_attrs[:contractor],
          amount: bid_attrs["amount"] || bid_attrs[:amount],
          message: bid_attrs["message"] || bid_attrs[:message] || "",
          timeline: bid_attrs["timeline"] || bid_attrs[:timeline] || "TBD",
          status: "pending",
          placed_at: DateTime.utc_now()
        }

        job_bids = Map.get(state.bids, job_id, [])
        state = put_in(state.bids[job_id], [bid | job_bids])

        updated_job = %{job | bid_count: length(job_bids) + 1}
        state = put_in(state.jobs[job_id], updated_job)

        broadcast("jobs", "job:updated", updated_job)
        broadcast("job:#{job_id}", "bid:placed", bid)
        {:reply, {:ok, bid}, state}
    end
  end

  @impl true
  def handle_call({:accept_bid, job_id, bid_id}, _from, state) do
    job_bids = Map.get(state.bids, job_id, [])

    case Enum.find(job_bids, &(&1.id == bid_id)) do
      nil ->
        {:reply, {:error, :bid_not_found}, state}

      bid ->
        updated_bids =
          Enum.map(job_bids, fn b ->
            if b.id == bid_id, do: %{b | status: "accepted"}, else: %{b | status: "rejected"}
          end)

        state = put_in(state.bids[job_id], updated_bids)

        job = state.jobs[job_id]
        updated_job = %{job | status: "awarded"}
        state = put_in(state.jobs[job_id], updated_job)

        broadcast("jobs", "job:updated", updated_job)
        broadcast("job:#{job_id}", "bid:accepted", %{bid | status: "accepted"})
        {:reply, {:ok, %{bid | status: "accepted"}}, state}
    end
  end

  @impl true
  def handle_call({:list_bids, job_id}, _from, state) do
    bids = Map.get(state.bids, job_id, []) |> Enum.reverse()
    {:reply, bids, state}
  end

  @impl true
  def handle_call({:send_message, conversation_id, attrs}, _from, state) do
    {id, state} = next_id(state)

    message = %{
      id: "msg_#{id}",
      conversation_id: conversation_id,
      sender: attrs["sender"] || attrs[:sender],
      body: attrs["body"] || attrs[:body],
      sent_at: DateTime.utc_now()
    }

    convo_messages = Map.get(state.messages, conversation_id, [])
    state = put_in(state.messages[conversation_id], [message | convo_messages])

    broadcast("chat:#{conversation_id}", "message:new", message)
    {:reply, {:ok, message}, state}
  end

  @impl true
  def handle_call({:list_messages, conversation_id}, _from, state) do
    messages = Map.get(state.messages, conversation_id, []) |> Enum.reverse()
    {:reply, messages, state}
  end

  # --- Helpers ---

  defp next_id(state) do
    {state.next_id, %{state | next_id: state.next_id + 1}}
  end

  defp broadcast(topic, event, payload) do
    PubSub.broadcast(FtwRealtime.PubSub, topic, {event, payload})
  end

  defp seed_demo_data(state) do
    demo_jobs = [
      %{
        title: "Kitchen Remodel - Full Gut",
        description: "Complete kitchen renovation. Demo existing cabinets, new custom cabinets, granite countertops, tile backsplash, new appliances.",
        category: "remodeling",
        budget_min: 25_000,
        budget_max: 45_000,
        location: "Oxford, MS",
        homeowner: "Sarah Johnson"
      },
      %{
        title: "Roof Replacement - 2,400 sq ft",
        description: "Tear off existing shingles, inspect decking, install new architectural shingles. Ridge vent and drip edge included.",
        category: "roofing",
        budget_min: 8_000,
        budget_max: 14_000,
        location: "Tupelo, MS",
        homeowner: "Mike Davis"
      },
      %{
        title: "Bathroom Addition - Master Suite",
        description: "Add master bathroom to existing master bedroom. Plumbing rough-in needed. Walk-in shower, double vanity, heated floors.",
        category: "additions",
        budget_min: 18_000,
        budget_max: 30_000,
        location: "Jackson, MS",
        homeowner: "Lisa Chen"
      },
      %{
        title: "Deck Build - 16x20 Composite",
        description: "New composite deck with aluminum railing. Includes footings, ledger board, and built-in bench seating.",
        category: "outdoor",
        budget_min: 12_000,
        budget_max: 20_000,
        location: "Starkville, MS",
        homeowner: "James Mitchell"
      }
    ]

    Enum.reduce(demo_jobs, state, fn job_attrs, acc ->
      {id, acc} = next_id(acc)
      job_id = "job_#{id}"

      job = %{
        id: job_id,
        title: job_attrs.title,
        description: job_attrs.description,
        category: job_attrs.category,
        budget_min: job_attrs.budget_min,
        budget_max: job_attrs.budget_max,
        location: job_attrs.location,
        homeowner: job_attrs.homeowner,
        status: "open",
        posted_at: DateTime.utc_now() |> DateTime.add(-Enum.random(1..72), :hour),
        bid_count: 0
      }

      put_in(acc.jobs[job_id], job)
    end)
  end
end
