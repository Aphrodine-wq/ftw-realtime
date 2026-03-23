defmodule FtwRealtime.Marketplace do
  @moduledoc """
  Marketplace context. Manages jobs, bids, conversations, and messages.
  All writes broadcast via PubSub so channels and LiveViews update instantly.
  """
  import Ecto.Query

  alias FtwRealtime.Repo
  alias FtwRealtime.Marketplace.{User, Job, Bid, Conversation, Message}
  alias Phoenix.PubSub

  # --- Users ---

  def get_user(id), do: Repo.get(User, id)

  def get_user_by_email(email), do: Repo.get_by(User, email: email)

  def register_user(attrs) do
    %User{}
    |> User.registration_changeset(attrs)
    |> Repo.insert()
  end

  def create_user(attrs) do
    %User{}
    |> User.changeset(attrs)
    |> Repo.insert()
  end

  def authenticate_user(email, password) do
    user = get_user_by_email(email)

    if user && User.verify_password(user, password) do
      {:ok, user}
    else
      Argon2.no_user_verify()
      {:error, :invalid_credentials}
    end
  end

  # --- Jobs ---

  @default_page_size 20
  @max_page_size 100

  def list_jobs(opts \\ []) do
    status = Keyword.get(opts, :status)
    category = Keyword.get(opts, :category)
    limit = opts |> Keyword.get(:limit, @default_page_size) |> min(@max_page_size)
    after_cursor = Keyword.get(opts, :after)

    query =
      Job
      |> maybe_filter_status(status)
      |> maybe_filter_category(category)
      |> maybe_after_cursor(after_cursor)
      |> order_by([j], desc: j.inserted_at)
      |> limit(^(limit + 1))
      |> preload(:homeowner)

    results = Repo.all(query)
    has_more = length(results) > limit
    jobs = Enum.take(results, limit)

    next_cursor =
      if has_more do
        jobs |> List.last() |> Map.get(:id)
      end

    %{jobs: jobs, has_more: has_more, next_cursor: next_cursor}
  end

  def get_job(id) do
    Job
    |> preload(:homeowner)
    |> Repo.get(id)
  end

  def post_job(attrs) do
    result =
      %Job{}
      |> Job.changeset(attrs)
      |> Repo.insert()

    case result do
      {:ok, job} ->
        job = Repo.preload(job, :homeowner)
        broadcast("jobs", "job:posted", serialize_job(job))
        {:ok, job}

      error ->
        error
    end
  end

  # --- Bids ---

  def list_bids(job_id) do
    Bid
    |> where([b], b.job_id == ^job_id)
    |> order_by([b], asc: b.inserted_at)
    |> preload(:contractor)
    |> Repo.all()
  end

  def place_bid(job_id, attrs) do
    attrs = Map.put(attrs, "job_id", job_id)

    Repo.transaction(fn ->
      case Repo.get(Job, job_id) do
        nil ->
          Repo.rollback(:job_not_found)

        %{status: status} when status != :open ->
          Repo.rollback(:job_not_accepting_bids)

        job ->
          bid_result =
            %Bid{}
            |> Bid.changeset(attrs)
            |> Repo.insert()

          case bid_result do
            {:ok, bid} ->
              new_count = count_bids(job_id)
              job |> Ecto.Changeset.change(bid_count: new_count) |> Repo.update!()

              bid = Repo.preload(bid, :contractor)
              updated_job = Repo.preload(Repo.get!(Job, job_id), :homeowner)

              broadcast("jobs", "job:updated", serialize_job(updated_job))
              broadcast("job:#{job_id}", "bid:placed", serialize_bid(bid))
              bid

            {:error, changeset} ->
              Repo.rollback(changeset)
          end
      end
    end)
  end

  def accept_bid(job_id, bid_id) do
    Repo.transaction(fn ->
      job = Repo.get!(Job, job_id)

      if job.status != :open do
        Repo.rollback(:job_not_accepting_bids)
      end

      case Repo.get_by(Bid, id: bid_id, job_id: job_id) do
        nil ->
          Repo.rollback(:bid_not_found)

        bid ->
          bid |> Ecto.Changeset.change(status: :accepted) |> Repo.update!()

          from(b in Bid, where: b.job_id == ^job_id and b.id != ^bid_id and b.status == :pending)
          |> Repo.update_all(set: [status: :rejected])

          job |> Ecto.Changeset.change(status: :awarded) |> Repo.update!()

          accepted_bid = Repo.preload(Repo.get!(Bid, bid_id), :contractor)
          updated_job = Repo.preload(Repo.get!(Job, job_id), :homeowner)

          broadcast("jobs", "job:updated", serialize_job(updated_job))
          broadcast("job:#{job_id}", "bid:accepted", serialize_bid(accepted_bid))
          accepted_bid
      end
    end)
  end

  # --- Job State Machine ---
  #
  # Valid transitions:
  #   open       -> awarded (via accept_bid only), cancelled
  #   awarded    -> in_progress, cancelled
  #   in_progress -> completed, disputed
  #   disputed   -> in_progress (resolved), cancelled
  #   completed  -> (terminal)
  #   cancelled  -> (terminal)

  @valid_transitions %{
    open: [:cancelled],
    awarded: [:in_progress, :cancelled],
    in_progress: [:completed, :disputed],
    disputed: [:in_progress, :cancelled],
    completed: [],
    cancelled: []
  }

  def transition_job(job_id, new_status, user_id) do
    Repo.transaction(fn ->
      job = Repo.get!(Job, job_id) |> Repo.preload(:homeowner)
      allowed = Map.get(@valid_transitions, job.status, [])

      cond do
        new_status not in allowed ->
          Repo.rollback({:invalid_transition, job.status, new_status})

        new_status == :in_progress && !winning_contractor?(job_id, user_id) ->
          Repo.rollback(:only_winning_contractor)

        new_status in [:cancelled] && job.homeowner_id != user_id ->
          Repo.rollback(:only_homeowner_can_cancel)

        true ->
          job |> Ecto.Changeset.change(status: new_status) |> Repo.update!()
          updated_job = Repo.preload(Repo.get!(Job, job_id), :homeowner)
          broadcast("jobs", "job:updated", serialize_job(updated_job))
          updated_job
      end
    end)
  end

  defp winning_contractor?(job_id, user_id) do
    Repo.exists?(
      from(b in Bid,
        where: b.job_id == ^job_id and b.contractor_id == ^user_id and b.status == :accepted
      )
    )
  end

  # --- Conversations & Messages ---

  def get_or_create_conversation(job_id, homeowner_id, contractor_id) do
    case Repo.get_by(Conversation,
           job_id: job_id,
           homeowner_id: homeowner_id,
           contractor_id: contractor_id
         ) do
      nil ->
        %Conversation{}
        |> Conversation.changeset(%{
          job_id: job_id,
          homeowner_id: homeowner_id,
          contractor_id: contractor_id
        })
        |> Repo.insert()

      conversation ->
        {:ok, conversation}
    end
  end

  def list_messages(conversation_id) do
    Message
    |> where([m], m.conversation_id == ^conversation_id)
    |> order_by([m], asc: m.inserted_at)
    |> preload(:sender)
    |> Repo.all()
  end

  def send_message(conversation_id, attrs) do
    attrs = Map.put(attrs, "conversation_id", conversation_id)

    result =
      %Message{}
      |> Message.changeset(attrs)
      |> Repo.insert()

    case result do
      {:ok, message} ->
        message = Repo.preload(message, :sender)
        broadcast("chat:#{conversation_id}", "message:new", serialize_message(message))
        {:ok, message}

      error ->
        error
    end
  end

  # --- Seeds ---

  def seed_demo_data do
    alias FtwRealtime.Marketplace.User

    # Create demo users
    {:ok, homeowner1} =
      register_user(%{
        email: "sarah@example.com",
        name: "Sarah Johnson",
        role: :homeowner,
        location: "Oxford, MS",
        password: "password123"
      })

    {:ok, homeowner2} =
      register_user(%{
        email: "mike@example.com",
        name: "Mike Davis",
        role: :homeowner,
        location: "Tupelo, MS",
        password: "password123"
      })

    {:ok, homeowner3} =
      register_user(%{
        email: "lisa@example.com",
        name: "Lisa Chen",
        role: :homeowner,
        location: "Jackson, MS",
        password: "password123"
      })

    {:ok, homeowner4} =
      register_user(%{
        email: "james.m@example.com",
        name: "James Mitchell",
        role: :homeowner,
        location: "Starkville, MS",
        password: "password123"
      })

    {:ok, _contractor1} =
      register_user(%{
        email: "contractor1@example.com",
        name: "Bobby Ray Construction",
        role: :contractor,
        location: "Oxford, MS",
        license_number: "MS-12345",
        rating: 4.8,
        jobs_completed: 47,
        password: "password123"
      })

    {:ok, _contractor2} =
      register_user(%{
        email: "contractor2@example.com",
        name: "Delta Builds LLC",
        role: :contractor,
        location: "Tupelo, MS",
        license_number: "MS-67890",
        rating: 4.5,
        jobs_completed: 23,
        password: "password123"
      })

    # Create demo jobs
    demo_jobs = [
      %{
        title: "Kitchen Remodel - Full Gut",
        description:
          "Complete kitchen renovation. Demo existing cabinets, new custom cabinets, granite countertops, tile backsplash, new appliances.",
        category: "remodeling",
        budget_min: 25_000,
        budget_max: 45_000,
        location: "Oxford, MS",
        homeowner_id: homeowner1.id
      },
      %{
        title: "Roof Replacement - 2,400 sq ft",
        description:
          "Tear off existing shingles, inspect decking, install new architectural shingles. Ridge vent and drip edge included.",
        category: "roofing",
        budget_min: 8_000,
        budget_max: 14_000,
        location: "Tupelo, MS",
        homeowner_id: homeowner2.id
      },
      %{
        title: "Bathroom Addition - Master Suite",
        description:
          "Add master bathroom to existing master bedroom. Plumbing rough-in needed. Walk-in shower, double vanity, heated floors.",
        category: "additions",
        budget_min: 18_000,
        budget_max: 30_000,
        location: "Jackson, MS",
        homeowner_id: homeowner3.id
      },
      %{
        title: "Deck Build - 16x20 Composite",
        description:
          "New composite deck with aluminum railing. Includes footings, ledger board, and built-in bench seating.",
        category: "outdoor",
        budget_min: 12_000,
        budget_max: 20_000,
        location: "Starkville, MS",
        homeowner_id: homeowner4.id
      }
    ]

    Enum.each(demo_jobs, &post_job/1)

    :ok
  end

  # --- Private ---

  defp count_bids(job_id) do
    Bid |> where([b], b.job_id == ^job_id) |> Repo.aggregate(:count)
  end

  defp maybe_filter_status(query, nil), do: query
  defp maybe_filter_status(query, status), do: where(query, [j], j.status == ^status)

  defp maybe_filter_category(query, nil), do: query
  defp maybe_filter_category(query, category), do: where(query, [j], j.category == ^category)

  defp maybe_after_cursor(query, nil), do: query

  defp maybe_after_cursor(query, cursor_id) do
    case Repo.get(Job, cursor_id) do
      nil -> query
      cursor_job -> where(query, [j], j.inserted_at < ^cursor_job.inserted_at)
    end
  end

  defp broadcast(topic, event, payload) do
    PubSub.broadcast(FtwRealtime.PubSub, topic, {event, payload})
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

  defp serialize_message(message) do
    %{
      id: message.id,
      conversation_id: message.conversation_id,
      body: message.body,
      sender: serialize_user(message.sender),
      sent_at: message.inserted_at
    }
  end

  defp serialize_user(%User{} = user) do
    %{
      id: user.id,
      name: user.name,
      role: user.role,
      location: user.location,
      rating: user.rating
    }
  end

  defp serialize_user(_), do: nil
end
