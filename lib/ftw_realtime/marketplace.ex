defmodule FtwRealtime.Marketplace do
  @moduledoc """
  Marketplace context. Manages jobs, bids, conversations, and messages.
  All writes broadcast via PubSub so channels and LiveViews update instantly.
  """
  import Ecto.Query

  alias FtwRealtime.Repo
  alias FtwRealtime.Marketplace.{User, Job, Bid, Conversation, Message}
  alias FtwRealtime.Marketplace.{Client, Estimate, LineItem, Invoice, Project, Notification}
  alias FtwRealtime.Marketplace.Review
  alias FtwRealtime.Marketplace.{Upload, UserSetting}
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

              # Notify homeowner about new bid
              enqueue_notification(
                "bid_received",
                job.homeowner_id,
                "New bid on #{job.title}",
                "A contractor bid $#{bid.amount} on your job",
                %{job_id: job.id, bid_id: bid.id}
              )

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

          # Notify winning contractor
          enqueue_notification(
            "bid_accepted",
            bid.contractor_id,
            "Your bid was accepted!",
            "Your bid on #{job.title} was accepted",
            %{job_id: job.id, bid_id: bid.id}
          )

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

  def get_conversation(id), do: Repo.get(Conversation, id)

  def conversation_participant?(conversation_id, user_id) do
    Repo.exists?(
      from(c in Conversation,
        where:
          c.id == ^conversation_id and
            (c.homeowner_id == ^user_id or c.contractor_id == ^user_id)
      )
    )
  end

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

  # --- Estimates ---

  def list_estimates(contractor_id, opts \\ []) do
    status = Keyword.get(opts, :status)

    Estimate
    |> where([e], e.contractor_id == ^contractor_id)
    |> maybe_filter_status(status)
    |> order_by([e], desc: e.inserted_at)
    |> preload([:line_items, :client])
    |> Repo.all()
  end

  def get_estimate(id) do
    Estimate
    |> preload([:line_items, :client])
    |> Repo.get(id)
  end

  def create_estimate(attrs) do
    result =
      %Estimate{}
      |> Estimate.changeset(attrs)
      |> Ecto.Changeset.cast_assoc(:line_items, with: &LineItem.changeset/2)
      |> Repo.insert()

    case result do
      {:ok, estimate} -> {:ok, Repo.preload(estimate, [:line_items, :client])}
      error -> error
    end
  end

  def update_estimate(id, attrs) do
    case Repo.get(Estimate, id) do
      nil -> {:error, :not_found}
      estimate -> estimate |> Estimate.changeset(attrs) |> Repo.update()
    end
  end

  def delete_estimate(id) do
    case Repo.get(Estimate, id) do
      nil -> {:error, :not_found}
      estimate -> Repo.delete(estimate)
    end
  end

  # --- Invoices ---

  def list_invoices(contractor_id, opts \\ []) do
    status = Keyword.get(opts, :status)

    Invoice
    |> where([i], i.contractor_id == ^contractor_id)
    |> maybe_filter_status(status)
    |> order_by([i], desc: i.inserted_at)
    |> preload([:client])
    |> Repo.all()
  end

  def get_invoice(id) do
    Invoice
    |> preload([:client, :estimate, :project])
    |> Repo.get(id)
  end

  def create_invoice(attrs) do
    %Invoice{}
    |> Invoice.changeset(attrs)
    |> Repo.insert()
  end

  def update_invoice(id, attrs) do
    case Repo.get(Invoice, id) do
      nil -> {:error, :not_found}
      invoice -> invoice |> Invoice.changeset(attrs) |> Repo.update()
    end
  end

  # --- Projects ---

  def list_projects(user_id, opts \\ []) do
    status = Keyword.get(opts, :status)

    Project
    |> where([p], p.contractor_id == ^user_id or p.homeowner_id == ^user_id)
    |> maybe_filter_status(status)
    |> order_by([p], desc: p.inserted_at)
    |> preload([:contractor, :homeowner])
    |> Repo.all()
  end

  def get_project(id) do
    Project
    |> preload([:contractor, :homeowner])
    |> Repo.get(id)
  end

  def create_project(attrs) do
    %Project{}
    |> Project.changeset(attrs)
    |> Repo.insert()
  end

  def update_project(id, attrs) do
    case Repo.get(Project, id) do
      nil -> {:error, :not_found}
      project -> project |> Project.changeset(attrs) |> Repo.update()
    end
  end

  # --- Clients ---

  def list_clients(contractor_id) do
    Client
    |> where([c], c.contractor_id == ^contractor_id)
    |> order_by([c], asc: c.name)
    |> Repo.all()
  end

  def get_client(id), do: Repo.get(Client, id)

  def create_client(attrs) do
    %Client{}
    |> Client.changeset(attrs)
    |> Repo.insert()
  end

  def update_client(id, attrs) do
    case Repo.get(Client, id) do
      nil -> {:error, :not_found}
      client -> client |> Client.changeset(attrs) |> Repo.update()
    end
  end

  def delete_client(id) do
    case Repo.get(Client, id) do
      nil -> {:error, :not_found}
      client -> Repo.delete(client)
    end
  end

  # --- Notifications ---

  def list_notifications(user_id, opts \\ []) do
    read_filter = Keyword.get(opts, :read)

    query =
      Notification
      |> where([n], n.user_id == ^user_id)
      |> order_by([n], desc: n.inserted_at)

    query =
      case read_filter do
        nil -> query
        val -> where(query, [n], n.read == ^val)
      end

    Repo.all(query)
  end

  def mark_read(notification_id) do
    case Repo.get(Notification, notification_id) do
      nil -> {:error, :not_found}
      notification -> notification |> Ecto.Changeset.change(read: true) |> Repo.update()
    end
  end

  def mark_all_read(user_id) do
    from(n in Notification, where: n.user_id == ^user_id and n.read == false)
    |> Repo.update_all(set: [read: true])
  end

  def create_notification(attrs) do
    result =
      %Notification{}
      |> Notification.changeset(attrs)
      |> Repo.insert()

    case result do
      {:ok, notification} ->
        broadcast(
          "user:#{notification.user_id}",
          "notification:new",
          serialize_notification(notification)
        )

        {:ok, notification}

      error ->
        error
    end
  end

  # --- Reviews ---

  def list_reviews_for_user(user_id, opts \\ []) do
    limit = Keyword.get(opts, :limit, 20)

    Review
    |> where([r], r.reviewed_id == ^user_id)
    |> order_by([r], desc: r.inserted_at)
    |> limit(^limit)
    |> preload([:reviewer, :job])
    |> Repo.all()
  end

  def list_reviews_by_user(user_id) do
    Review
    |> where([r], r.reviewer_id == ^user_id)
    |> order_by([r], desc: r.inserted_at)
    |> preload([:reviewed, :job])
    |> Repo.all()
  end

  def get_review(id) do
    Review
    |> preload([:reviewer, :reviewed, :job])
    |> Repo.get(id)
  end

  def create_review(attrs) do
    result =
      %Review{}
      |> Review.changeset(attrs)
      |> Repo.insert()

    case result do
      {:ok, review} -> {:ok, Repo.preload(review, [:reviewer, :reviewed, :job])}
      error -> error
    end
  end

  def respond_to_review(review_id, response_text) do
    case Repo.get(Review, review_id) do
      nil -> {:error, :not_found}
      review -> review |> Ecto.Changeset.change(response: response_text) |> Repo.update()
    end
  end

  # --- Uploads ---

  @max_upload_size 10_485_760

  def list_uploads(entity_type, entity_id) do
    Upload
    |> where([u], u.entity_type == ^entity_type and u.entity_id == ^entity_id)
    |> order_by([u], desc: u.inserted_at)
    |> Repo.all()
  end

  def get_upload(id), do: Repo.get(Upload, id)

  def create_upload(attrs) do
    size = Map.get(attrs, :size) || Map.get(attrs, "size")

    if is_integer(size) and size > @max_upload_size do
      {:error, :file_too_large}
    else
      %Upload{}
      |> Upload.changeset(attrs)
      |> Repo.insert()
    end
  end

  def delete_upload(id) do
    case Repo.get(Upload, id) do
      nil -> {:error, :not_found}
      upload -> Repo.delete(upload)
    end
  end

  @doc false
  def serialize_upload(upload) do
    %{
      id: upload.id,
      filename: upload.filename,
      content_type: upload.content_type,
      size: upload.size,
      url: "/uploads/#{upload.path}",
      entity_type: upload.entity_type,
      entity_id: upload.entity_id,
      created_at: upload.inserted_at
    }
  end

  # --- User Settings ---

  def get_settings(user_id) do
    case Repo.get_by(UserSetting, user_id: user_id) do
      nil ->
        {:ok, settings} =
          %UserSetting{}
          |> UserSetting.changeset(%{user_id: user_id})
          |> Repo.insert()

        settings

      settings ->
        settings
    end
  end

  def update_settings(user_id, attrs) do
    settings = get_settings(user_id)

    settings
    |> UserSetting.changeset(attrs)
    |> Repo.update()
  end

  # --- Seeds ---

  def seed_demo_data do
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

    {:ok, contractor1} =
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

    # --- Clients for contractor1 (Bobby Ray Construction) ---

    {:ok, client1} =
      create_client(%{
        name: "Sarah Johnson",
        email: "sarah@example.com",
        phone: "662-555-0101",
        address: "142 Oak Lane, Oxford, MS 38655",
        notes: "Prefers text communication. Has two dogs.",
        contractor_id: contractor1.id,
        user_id: homeowner1.id
      })

    {:ok, client2} =
      create_client(%{
        name: "Mike Davis",
        email: "mike@example.com",
        phone: "662-555-0202",
        address: "88 Magnolia Dr, Tupelo, MS 38801",
        notes: "Works from home. Available anytime.",
        contractor_id: contractor1.id,
        user_id: homeowner2.id
      })

    {:ok, client3} =
      create_client(%{
        name: "Lisa Chen",
        email: "lisa@example.com",
        phone: "601-555-0303",
        address: "2210 State St, Jackson, MS 39202",
        contractor_id: contractor1.id,
        user_id: homeowner3.id
      })

    {:ok, client4} =
      create_client(%{
        name: "James Mitchell",
        email: "james.m@example.com",
        phone: "662-555-0404",
        address: "501 University Dr, Starkville, MS 39759",
        notes: "New construction only. No weekends.",
        contractor_id: contractor1.id,
        user_id: homeowner4.id
      })

    {:ok, _client5} =
      create_client(%{
        name: "Karen Wells",
        email: "karen.wells@example.com",
        phone: "662-555-0505",
        address: "77 Country Club Rd, Oxford, MS 38655",
        notes: "Referred by Sarah Johnson.",
        contractor_id: contractor1.id
      })

    {:ok, client6} =
      create_client(%{
        name: "Tom Bradley",
        email: "tom.bradley@example.com",
        phone: "662-555-0606",
        address: "1900 Jackson Ave, Oxford, MS 38655",
        notes: "Commercial property manager. Multiple properties.",
        contractor_id: contractor1.id
      })

    # --- Estimates for contractor1 ---

    {:ok, estimate1} =
      create_estimate(%{
        title: "Kitchen Remodel - Johnson Residence",
        description:
          "Full kitchen gut and remodel including cabinets, countertops, backsplash, flooring, and appliance installation.",
        total: 3_850_000,
        status: :sent,
        valid_until: ~U[2026-04-22 00:00:00Z],
        notes: "Price includes debris removal. Appliances not included in estimate.",
        contractor_id: contractor1.id,
        client_id: client1.id,
        line_items: [
          %{
            description: "Demo existing kitchen",
            quantity: 1.0,
            unit: "lot",
            unit_price: 350_000,
            total: 350_000,
            category: "demolition",
            sort_order: 0
          },
          %{
            description: "Custom shaker cabinets (12 linear ft uppers, 16 linear ft lowers)",
            quantity: 1.0,
            unit: "set",
            unit_price: 1_200_000,
            total: 1_200_000,
            category: "cabinets",
            sort_order: 1
          },
          %{
            description: "Granite countertops - Level 3 Bianco Antico",
            quantity: 42.0,
            unit: "sqft",
            unit_price: 18_500,
            total: 777_000,
            category: "countertops",
            sort_order: 2
          },
          %{
            description: "Subway tile backsplash with herringbone accent",
            quantity: 30.0,
            unit: "sqft",
            unit_price: 2_500,
            total: 75_000,
            category: "tile",
            sort_order: 3
          },
          %{
            description: "LVP flooring - kitchen and breakfast nook",
            quantity: 280.0,
            unit: "sqft",
            unit_price: 850,
            total: 238_000,
            category: "flooring",
            sort_order: 4
          }
        ]
      })

    {:ok, _estimate2} =
      create_estimate(%{
        title: "Roof Replacement - Davis Property",
        description:
          "Tear-off and replacement of existing roof. Architectural shingles, new underlayment, ridge vent.",
        total: 1_185_000,
        status: :accepted,
        valid_until: ~U[2026-04-15 00:00:00Z],
        notes: "Warranty: 25-year manufacturer, 5-year labor.",
        contractor_id: contractor1.id,
        client_id: client2.id,
        line_items: [
          %{
            description: "Tear-off existing shingles (2 layers)",
            quantity: 24.0,
            unit: "squares",
            unit_price: 12_500,
            total: 300_000,
            category: "demolition",
            sort_order: 0
          },
          %{
            description: "GAF Timberline HDZ Architectural Shingles",
            quantity: 24.0,
            unit: "squares",
            unit_price: 22_000,
            total: 528_000,
            category: "roofing",
            sort_order: 1
          },
          %{
            description: "Synthetic underlayment",
            quantity: 24.0,
            unit: "squares",
            unit_price: 4_500,
            total: 108_000,
            category: "roofing",
            sort_order: 2
          },
          %{
            description: "Ridge vent and drip edge",
            quantity: 1.0,
            unit: "lot",
            unit_price: 125_000,
            total: 125_000,
            category: "roofing",
            sort_order: 3
          },
          %{
            description: "Debris removal and dump fees",
            quantity: 1.0,
            unit: "lot",
            unit_price: 124_000,
            total: 124_000,
            category: "cleanup",
            sort_order: 4
          }
        ]
      })

    {:ok, _estimate3} =
      create_estimate(%{
        title: "Master Bath Addition - Chen Home",
        description:
          "New master bathroom addition with walk-in shower, double vanity, heated tile floors.",
        total: 2_475_000,
        status: :draft,
        contractor_id: contractor1.id,
        client_id: client3.id,
        line_items: [
          %{
            description: "Framing and structural work",
            quantity: 1.0,
            unit: "lot",
            unit_price: 450_000,
            total: 450_000,
            category: "framing",
            sort_order: 0
          },
          %{
            description: "Plumbing rough-in and fixtures",
            quantity: 1.0,
            unit: "lot",
            unit_price: 625_000,
            total: 625_000,
            category: "plumbing",
            sort_order: 1
          },
          %{
            description: "Electrical rough-in and fixtures",
            quantity: 1.0,
            unit: "lot",
            unit_price: 275_000,
            total: 275_000,
            category: "electrical",
            sort_order: 2
          }
        ]
      })

    {:ok, _estimate4} =
      create_estimate(%{
        title: "Composite Deck Build - Mitchell Residence",
        description:
          "16x20 composite deck with aluminum railing, footings, ledger board, and built-in bench.",
        total: 1_640_000,
        status: :viewed,
        valid_until: ~U[2026-05-01 00:00:00Z],
        contractor_id: contractor1.id,
        client_id: client4.id,
        line_items: [
          %{
            description: "Concrete footings (8 piers)",
            quantity: 8.0,
            unit: "each",
            unit_price: 35_000,
            total: 280_000,
            category: "foundation",
            sort_order: 0
          },
          %{
            description: "Pressure-treated framing lumber",
            quantity: 1.0,
            unit: "lot",
            unit_price: 320_000,
            total: 320_000,
            category: "framing",
            sort_order: 1
          },
          %{
            description: "Trex Transcend composite decking",
            quantity: 320.0,
            unit: "sqft",
            unit_price: 2_200,
            total: 704_000,
            category: "decking",
            sort_order: 2
          },
          %{
            description: "Aluminum railing system",
            quantity: 56.0,
            unit: "linear ft",
            unit_price: 4_500,
            total: 252_000,
            category: "railing",
            sort_order: 3
          }
        ]
      })

    {:ok, _estimate5} =
      create_estimate(%{
        title: "Office Renovation - Bradley Properties",
        description:
          "Commercial office space renovation. New drywall, paint, flooring, and lighting.",
        total: 890_000,
        status: :expired,
        valid_until: ~U[2026-02-28 00:00:00Z],
        notes: "Quote expired. Client may request updated pricing.",
        contractor_id: contractor1.id,
        client_id: client6.id,
        line_items: [
          %{
            description: "Drywall and framing (4 offices)",
            quantity: 1.0,
            unit: "lot",
            unit_price: 285_000,
            total: 285_000,
            category: "drywall",
            sort_order: 0
          },
          %{
            description: "Interior paint - walls and trim",
            quantity: 1800.0,
            unit: "sqft",
            unit_price: 150,
            total: 270_000,
            category: "paint",
            sort_order: 1
          },
          %{
            description: "Commercial carpet tile",
            quantity: 1200.0,
            unit: "sqft",
            unit_price: 175,
            total: 210_000,
            category: "flooring",
            sort_order: 2
          },
          %{
            description: "LED panel lighting (drop ceiling)",
            quantity: 18.0,
            unit: "each",
            unit_price: 6_944,
            total: 125_000,
            category: "electrical",
            sort_order: 3
          }
        ]
      })

    # --- Invoices ---

    {:ok, _invoice1} =
      create_invoice(%{
        invoice_number: "BRC-2026-001",
        amount: 1_185_000,
        status: :paid,
        due_date: ~D[2026-03-15],
        paid_at: ~U[2026-03-12 14:30:00Z],
        notes: "Roof replacement - paid in full via QuickBooks.",
        contractor_id: contractor1.id,
        client_id: client2.id,
        estimate_id: estimate1.id
      })

    {:ok, _invoice2} =
      create_invoice(%{
        invoice_number: "BRC-2026-002",
        amount: 1_925_000,
        status: :sent,
        due_date: ~D[2026-04-01],
        notes: "Kitchen remodel - 50% deposit. Balance due at completion.",
        contractor_id: contractor1.id,
        client_id: client1.id
      })

    {:ok, _invoice3} =
      create_invoice(%{
        invoice_number: "BRC-2026-003",
        amount: 820_000,
        status: :draft,
        due_date: ~D[2026-04-15],
        notes: "Deck build - materials deposit.",
        contractor_id: contractor1.id,
        client_id: client4.id
      })

    # --- Projects ---

    {:ok, _project1} =
      create_project(%{
        name: "Johnson Kitchen Remodel",
        description: "Full kitchen gut and remodel at 142 Oak Lane.",
        status: :active,
        start_date: ~D[2026-03-18],
        end_date: ~D[2026-05-15],
        budget: 3_850_000,
        spent: 1_925_000,
        contractor_id: contractor1.id,
        homeowner_id: homeowner1.id
      })

    {:ok, _project2} =
      create_project(%{
        name: "Davis Roof Replacement",
        description: "Full tear-off and re-roof at 88 Magnolia Dr.",
        status: :completed,
        start_date: ~D[2026-02-20],
        end_date: ~D[2026-03-05],
        budget: 1_185_000,
        spent: 1_185_000,
        contractor_id: contractor1.id,
        homeowner_id: homeowner2.id
      })

    {:ok, _project3} =
      create_project(%{
        name: "Mitchell Deck Build",
        description: "16x20 composite deck at 501 University Dr.",
        status: :planning,
        start_date: ~D[2026-04-10],
        budget: 1_640_000,
        spent: 0,
        contractor_id: contractor1.id,
        homeowner_id: homeowner4.id
      })

    # --- Notifications ---

    # Contractor1 notifications (5 total, 2 unread)
    create_notification(%{
      type: "bid_received",
      title: "New bid on Kitchen Remodel",
      body: "Delta Builds LLC submitted a bid of $42,500 on your Kitchen Remodel job.",
      read: true,
      metadata: %{job_id: "placeholder"},
      user_id: contractor1.id
    })

    create_notification(%{
      type: "payment_received",
      title: "Payment received - BRC-2026-001",
      body: "Mike Davis paid $11,850.00 for the Roof Replacement invoice.",
      read: true,
      metadata: %{invoice_number: "BRC-2026-001"},
      user_id: contractor1.id
    })

    create_notification(%{
      type: "estimate_viewed",
      title: "Estimate viewed by James Mitchell",
      body: "Your estimate for the Composite Deck Build was viewed.",
      read: true,
      metadata: %{},
      user_id: contractor1.id
    })

    create_notification(%{
      type: "message_received",
      title: "New message from Sarah Johnson",
      body: "Hey Bobby, just wanted to check on the cabinet delivery timeline.",
      read: false,
      metadata: %{},
      user_id: contractor1.id
    })

    create_notification(%{
      type: "project_milestone",
      title: "Project milestone: Davis Roof completed",
      body: "The Davis Roof Replacement project has been marked as completed.",
      read: false,
      metadata: %{},
      user_id: contractor1.id
    })

    # Homeowner1 (Sarah) notifications (3 total, 1 unread)
    create_notification(%{
      type: "bid_received",
      title: "New bid on your Kitchen Remodel",
      body: "Bobby Ray Construction submitted a bid on your Kitchen Remodel - Full Gut job.",
      read: true,
      metadata: %{},
      user_id: homeowner1.id
    })

    create_notification(%{
      type: "invoice_received",
      title: "Invoice from Bobby Ray Construction",
      body: "You have a new invoice (BRC-2026-002) for $19,250.00 due April 1.",
      read: true,
      metadata: %{invoice_number: "BRC-2026-002"},
      user_id: homeowner1.id
    })

    create_notification(%{
      type: "project_update",
      title: "Kitchen Remodel - Progress update",
      body:
        "Demo is complete. Cabinets arrive next week. Plumbing rough-in scheduled for Monday.",
      read: false,
      metadata: %{},
      user_id: homeowner1.id
    })

    # --- Reviews ---
    # Get job IDs for associating reviews
    all_jobs = Repo.all(from(j in Job, order_by: [asc: j.inserted_at], limit: 4))
    [job1, job2, job3, job4] = all_jobs

    # 4 reviews of contractor1 (Bobby Ray) by homeowners
    {:ok, review1} =
      create_review(%{
        rating: 5,
        comment:
          "Bobby Ray and his crew did an outstanding job on our kitchen remodel. They showed up on time every single day and kept the workspace clean. The custom cabinets look incredible and the tile work is flawless. Worth every penny.",
        reviewer_id: homeowner1.id,
        reviewed_id: contractor1.id,
        job_id: job1.id
      })

    {:ok, _review2} =
      create_review(%{
        rating: 5,
        comment:
          "Roof replacement done right. Tore off the old shingles, found some rotted decking, replaced it at no extra charge, and had the new roof on in three days. Communication was excellent throughout the entire project.",
        reviewer_id: homeowner2.id,
        reviewed_id: contractor1.id,
        job_id: job2.id
      })

    {:ok, review3} =
      create_review(%{
        rating: 4,
        comment:
          "Great work on the bathroom addition. Plumbing and tile were top notch. Only knock is the project ran about a week behind schedule due to a permit delay, but Bobby kept us informed the whole time.",
        reviewer_id: homeowner3.id,
        reviewed_id: contractor1.id,
        job_id: job3.id
      })

    {:ok, _review4} =
      create_review(%{
        rating: 5,
        comment:
          "The composite deck turned out better than we imagined. Solid footings, clean cuts, and the built-in bench is a nice touch. Bobby even helped us pick the right decking color. Highly recommend.",
        reviewer_id: homeowner4.id,
        reviewed_id: contractor1.id,
        job_id: job4.id
      })

    # Contractor responses on 2 reviews
    respond_to_review(
      review1.id,
      "Thank you Sarah! It was a pleasure working on your kitchen. Those shaker cabinets really turned out sharp. Let us know when you're ready to tackle that master bath."
    )

    respond_to_review(
      review3.id,
      "Appreciate the honest feedback Lisa. You're right about the delay — that permit holdup was frustrating for all of us. Glad the final result met your expectations."
    )

    # 2 reviews of homeowner sarah by contractor1
    {:ok, _review5} =
      create_review(%{
        rating: 5,
        comment:
          "Sarah was one of the best homeowners we've worked with. Clear about what she wanted, made decisions quickly, and always had materials access ready when we needed it. Would work with her again in a heartbeat.",
        reviewer_id: contractor1.id,
        reviewed_id: homeowner1.id
      })

    {:ok, _review6} =
      create_review(%{
        rating: 4,
        comment:
          "Good homeowner to work for. Responsive to calls and texts, paid on time. Only minor issue was a couple mid-project change orders, but we worked through them without any problems.",
        reviewer_id: contractor1.id,
        reviewed_id: homeowner2.id
      })

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

  defp enqueue_notification(type, user_id, title, body, metadata) do
    %{type: type, user_id: user_id, title: title, body: body, metadata: metadata}
    |> FtwRealtime.Workers.NotificationWorker.new()
    |> Oban.insert()
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

  @doc false
  def serialize_estimate(estimate) do
    %{
      id: estimate.id,
      title: estimate.title,
      description: estimate.description,
      total: estimate.total,
      status: estimate.status,
      valid_until: estimate.valid_until,
      notes: estimate.notes,
      contractor: serialize_user(estimate.contractor),
      client: serialize_client_or_nil(estimate.client),
      line_items: Enum.map(estimate.line_items || [], &serialize_line_item/1),
      job_id: estimate.job_id,
      created_at: estimate.inserted_at
    }
  end

  @doc false
  def serialize_line_item(li) do
    %{
      id: li.id,
      description: li.description,
      quantity: li.quantity,
      unit: li.unit,
      unit_price: li.unit_price,
      total: li.total,
      category: li.category,
      sort_order: li.sort_order
    }
  end

  @doc false
  def serialize_invoice(inv) do
    %{
      id: inv.id,
      invoice_number: inv.invoice_number,
      amount: inv.amount,
      status: inv.status,
      due_date: inv.due_date,
      paid_at: inv.paid_at,
      notes: inv.notes,
      contractor: serialize_user(inv.contractor),
      client: serialize_client_or_nil(inv.client),
      estimate_id: inv.estimate_id,
      project_id: inv.project_id,
      created_at: inv.inserted_at
    }
  end

  @doc false
  def serialize_project(proj) do
    %{
      id: proj.id,
      name: proj.name,
      description: proj.description,
      status: proj.status,
      start_date: proj.start_date,
      end_date: proj.end_date,
      budget: proj.budget,
      spent: proj.spent,
      contractor: serialize_user(proj.contractor),
      homeowner: serialize_user(proj.homeowner),
      job_id: proj.job_id,
      created_at: proj.inserted_at
    }
  end

  @doc false
  def serialize_client(%Client{} = client) do
    %{
      id: client.id,
      name: client.name,
      email: client.email,
      phone: client.phone,
      address: client.address,
      notes: client.notes,
      created_at: client.inserted_at
    }
  end

  def serialize_client(_), do: nil

  defp serialize_client_or_nil(%Client{} = client), do: serialize_client(client)
  defp serialize_client_or_nil(_), do: nil

  @doc false
  def serialize_review(review) do
    %{
      id: review.id,
      rating: review.rating,
      comment: review.comment,
      response: review.response,
      reviewer: serialize_user(review.reviewer),
      reviewed: serialize_user(review.reviewed),
      job_id: review.job_id,
      created_at: review.inserted_at
    }
  end

  @doc false
  def serialize_notification(notif) do
    %{
      id: notif.id,
      type: notif.type,
      title: notif.title,
      body: notif.body,
      read: notif.read,
      metadata: notif.metadata,
      created_at: notif.inserted_at
    }
  end
end
