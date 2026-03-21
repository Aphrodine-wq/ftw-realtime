defmodule FtwRealtimeWeb.MarketplaceLive do
  use FtwRealtimeWeb, :live_view

  alias FtwRealtime.Marketplace

  @impl true
  def mount(_params, _session, socket) do
    if connected?(socket) do
      Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "jobs")
    end

    jobs = Marketplace.list_jobs()

    socket =
      socket
      |> assign(:jobs, jobs)
      |> assign(:selected_job, nil)
      |> assign(:bids, [])
      |> assign(:show_post_form, false)
      |> assign(:show_bid_form, false)
      |> assign(:page_title, "FTW Marketplace")

    {:ok, socket}
  end

  @impl true
  def handle_info({"job:posted", job}, socket) do
    jobs = [job | socket.assigns.jobs]
    {:noreply, assign(socket, :jobs, jobs)}
  end

  @impl true
  def handle_info({"job:updated", updated_job}, socket) do
    jobs =
      Enum.map(socket.assigns.jobs, fn job ->
        if job.id == updated_job.id, do: updated_job, else: job
      end)

    socket = assign(socket, :jobs, jobs)

    socket =
      if socket.assigns.selected_job && socket.assigns.selected_job.id == updated_job.id do
        assign(socket, :selected_job, updated_job)
      else
        socket
      end

    {:noreply, socket}
  end

  @impl true
  def handle_info({"bid:placed", bid}, socket) do
    if socket.assigns.selected_job && socket.assigns.selected_job.id == bid.job_id do
      {:noreply, assign(socket, :bids, socket.assigns.bids ++ [bid])}
    else
      {:noreply, socket}
    end
  end

  @impl true
  def handle_info({"bid:accepted", bid}, socket) do
    bids =
      Enum.map(socket.assigns.bids, fn b ->
        cond do
          b.id == bid.id -> %{b | status: "accepted"}
          true -> %{b | status: "rejected"}
        end
      end)

    {:noreply, assign(socket, :bids, bids)}
  end

  @impl true
  def handle_event("select_job", %{"id" => job_id}, socket) do
    job = Marketplace.get_job(job_id)
    bids = Marketplace.list_bids(job_id)

    if socket.assigns.selected_job do
      Phoenix.PubSub.unsubscribe(FtwRealtime.PubSub, "job:#{socket.assigns.selected_job.id}")
    end

    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "job:#{job_id}")

    socket =
      socket
      |> assign(:selected_job, job)
      |> assign(:bids, bids)
      |> assign(:show_bid_form, false)

    {:noreply, socket}
  end

  @impl true
  def handle_event("toggle_post_form", _params, socket) do
    {:noreply, assign(socket, :show_post_form, !socket.assigns.show_post_form)}
  end

  @impl true
  def handle_event("toggle_bid_form", _params, socket) do
    {:noreply, assign(socket, :show_bid_form, !socket.assigns.show_bid_form)}
  end

  @impl true
  def handle_event("post_job", %{"job" => job_params}, socket) do
    case Marketplace.post_job(job_params) do
      {:ok, _job} ->
        {:noreply, assign(socket, :show_post_form, false)}

      {:error, _reason} ->
        {:noreply, socket}
    end
  end

  @impl true
  def handle_event("place_bid", %{"bid" => bid_params}, socket) do
    job_id = socket.assigns.selected_job.id

    case Marketplace.place_bid(job_id, bid_params) do
      {:ok, _bid} ->
        {:noreply, assign(socket, :show_bid_form, false)}

      {:error, _reason} ->
        {:noreply, socket}
    end
  end

  @impl true
  def handle_event("accept_bid", %{"bid-id" => bid_id}, socket) do
    job_id = socket.assigns.selected_job.id
    Marketplace.accept_bid(job_id, bid_id)
    {:noreply, socket}
  end

  @impl true
  def handle_event("back_to_list", _params, socket) do
    if socket.assigns.selected_job do
      Phoenix.PubSub.unsubscribe(FtwRealtime.PubSub, "job:#{socket.assigns.selected_job.id}")
    end

    socket =
      socket
      |> assign(:selected_job, nil)
      |> assign(:bids, [])
      |> assign(:show_bid_form, false)

    {:noreply, socket}
  end

  @impl true
  def render(assigns) do
    ~H"""
    <div class="min-h-screen bg-[#FDFCFA]">
      <header class="bg-[#0F1419] text-white px-6 py-4">
        <div class="max-w-6xl mx-auto flex items-center justify-between">
          <h1 class="text-xl font-semibold tracking-tight" style="font-family: -apple-system, 'SF Pro Display', system-ui, sans-serif;">
            FairTradeWorker
            <span class="text-[#059669] ml-2 text-sm font-normal">REALTIME</span>
          </h1>
          <div class="flex items-center gap-3">
            <span class="inline-block w-2 h-2 rounded-full bg-[#059669] animate-pulse"></span>
            <span class="text-sm text-gray-400">{length(@jobs)} active jobs</span>
          </div>
        </div>
      </header>

      <main class="max-w-6xl mx-auto px-6 py-8">
        <%= if @selected_job do %>
          <.job_detail job={@selected_job} bids={@bids} show_bid_form={@show_bid_form} />
        <% else %>
          <.job_list jobs={@jobs} show_post_form={@show_post_form} />
        <% end %>
      </main>
    </div>
    """
  end

  defp job_list(assigns) do
    ~H"""
    <div>
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-2xl font-semibold text-[#0F1419]">Live Job Board</h2>
        <button
          phx-click="toggle_post_form"
          class="px-4 py-2 bg-[#059669] text-white rounded-lg text-sm font-medium hover:bg-[#047857] transition-colors"
        >
          <%= if @show_post_form, do: "Cancel", else: "Post a Job" %>
        </button>
      </div>

      <%= if @show_post_form do %>
        <.post_job_form />
      <% end %>

      <div class="grid gap-4">
        <div
          :for={job <- @jobs}
          phx-click="select_job"
          phx-value-id={job.id}
          class="bg-white border border-gray-200 rounded-lg p-5 cursor-pointer hover:border-[#059669] transition-colors"
        >
          <div class="flex items-start justify-between">
            <div>
              <h3 class="font-semibold text-[#0F1419] text-lg">{job.title}</h3>
              <p class="text-gray-500 text-sm mt-1">{job.location} &middot; {job.category}</p>
            </div>
            <div class="text-right">
              <span class={"inline-block px-2 py-1 rounded text-xs font-medium #{status_color(job.status)}"}>
                {String.upcase(job.status)}
              </span>
              <p class="text-sm text-gray-500 mt-1">{job.bid_count} bids</p>
            </div>
          </div>
          <p class="text-gray-600 text-sm mt-3 line-clamp-2">{job.description}</p>
          <div class="flex items-center justify-between mt-4">
            <span class="text-[#059669] font-semibold">
              ${format_number(job.budget_min)} - ${format_number(job.budget_max)}
            </span>
            <span class="text-xs text-gray-400">{relative_time(job.posted_at)}</span>
          </div>
        </div>
      </div>
    </div>
    """
  end

  defp job_detail(assigns) do
    ~H"""
    <div>
      <button phx-click="back_to_list" class="text-sm text-gray-500 hover:text-[#059669] mb-4 flex items-center gap-1">
        &larr; Back to jobs
      </button>

      <div class="bg-white border border-gray-200 rounded-lg p-6 mb-6">
        <div class="flex items-start justify-between">
          <div>
            <h2 class="text-2xl font-semibold text-[#0F1419]">{@job.title}</h2>
            <p class="text-gray-500 mt-1">{@job.location} &middot; {@job.category} &middot; Posted by {@job.homeowner}</p>
          </div>
          <span class={"inline-block px-3 py-1 rounded text-sm font-medium #{status_color(@job.status)}"}>
            {String.upcase(@job.status)}
          </span>
        </div>
        <p class="text-gray-600 mt-4">{@job.description}</p>
        <div class="mt-4 text-[#059669] font-semibold text-lg">
          ${format_number(@job.budget_min)} - ${format_number(@job.budget_max)}
        </div>
      </div>

      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-[#0F1419]">Bids ({length(@bids)})</h3>
        <%= if @job.status == "open" do %>
          <button
            phx-click="toggle_bid_form"
            class="px-4 py-2 bg-[#059669] text-white rounded-lg text-sm font-medium hover:bg-[#047857] transition-colors"
          >
            <%= if @show_bid_form, do: "Cancel", else: "Place Bid" %>
          </button>
        <% end %>
      </div>

      <%= if @show_bid_form do %>
        <.bid_form />
      <% end %>

      <div class="grid gap-3">
        <%= if Enum.empty?(@bids) do %>
          <p class="text-gray-400 text-center py-8">No bids yet. Be the first!</p>
        <% end %>
        <div
          :for={bid <- @bids}
          class={"bg-white border rounded-lg p-4 #{bid_border_color(bid.status)}"}
        >
          <div class="flex items-start justify-between">
            <div>
              <span class="font-semibold text-[#0F1419]">{bid.contractor}</span>
              <span class={"ml-2 text-xs px-2 py-0.5 rounded #{bid_status_color(bid.status)}"}>
                {bid.status}
              </span>
            </div>
            <span class="text-[#059669] font-bold text-lg">${format_number(bid.amount)}</span>
          </div>
          <p class="text-gray-600 text-sm mt-2">{bid.message}</p>
          <div class="flex items-center justify-between mt-3">
            <span class="text-xs text-gray-400">Timeline: {bid.timeline}</span>
            <%= if bid.status == "pending" && @job.status == "open" do %>
              <button
                phx-click="accept_bid"
                phx-value-bid-id={bid.id}
                class="text-xs px-3 py-1 bg-[#059669] text-white rounded hover:bg-[#047857]"
              >
                Accept
              </button>
            <% end %>
          </div>
        </div>
      </div>
    </div>
    """
  end

  defp post_job_form(assigns) do
    ~H"""
    <form phx-submit="post_job" class="bg-white border border-gray-200 rounded-lg p-6 mb-6">
      <h3 class="font-semibold text-[#0F1419] mb-4">Post a New Job</h3>
      <div class="grid grid-cols-2 gap-4">
        <div class="col-span-2">
          <input name="job[title]" placeholder="Job title" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div class="col-span-2">
          <textarea name="job[description]" placeholder="Describe the work needed..." rows="3" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]"></textarea>
        </div>
        <div>
          <select name="job[category]"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]">
            <option value="remodeling">Remodeling</option>
            <option value="roofing">Roofing</option>
            <option value="additions">Additions</option>
            <option value="outdoor">Outdoor</option>
            <option value="plumbing">Plumbing</option>
            <option value="electrical">Electrical</option>
            <option value="hvac">HVAC</option>
            <option value="general">General</option>
          </select>
        </div>
        <div>
          <input name="job[location]" placeholder="City, State" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div>
          <input name="job[budget_min]" type="number" placeholder="Min budget" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div>
          <input name="job[budget_max]" type="number" placeholder="Max budget" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div class="col-span-2">
          <input name="job[homeowner]" placeholder="Your name" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
      </div>
      <button type="submit"
        class="mt-4 px-6 py-2 bg-[#059669] text-white rounded-lg text-sm font-medium hover:bg-[#047857] transition-colors">
        Post Job
      </button>
    </form>
    """
  end

  defp bid_form(assigns) do
    ~H"""
    <form phx-submit="place_bid" class="bg-white border border-gray-200 rounded-lg p-6 mb-4">
      <h3 class="font-semibold text-[#0F1419] mb-4">Submit Your Bid</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <input name="bid[contractor]" placeholder="Your name / company" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div>
          <input name="bid[amount]" type="number" placeholder="Bid amount ($)" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div>
          <input name="bid[timeline]" placeholder="Timeline (e.g. 2-3 weeks)" required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]" />
        </div>
        <div class="col-span-2">
          <textarea name="bid[message]" placeholder="Why you're the right contractor for this job..." rows="2"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:border-[#059669]"></textarea>
        </div>
      </div>
      <button type="submit"
        class="mt-4 px-6 py-2 bg-[#059669] text-white rounded-lg text-sm font-medium hover:bg-[#047857] transition-colors">
        Submit Bid
      </button>
    </form>
    """
  end

  defp status_color("open"), do: "bg-green-100 text-green-800"
  defp status_color("awarded"), do: "bg-blue-100 text-blue-800"
  defp status_color("completed"), do: "bg-gray-100 text-gray-800"
  defp status_color(_), do: "bg-gray-100 text-gray-600"

  defp bid_status_color("pending"), do: "bg-yellow-100 text-yellow-800"
  defp bid_status_color("accepted"), do: "bg-green-100 text-green-800"
  defp bid_status_color("rejected"), do: "bg-red-100 text-red-800"
  defp bid_status_color(_), do: "bg-gray-100 text-gray-600"

  defp bid_border_color("accepted"), do: "border-green-400"
  defp bid_border_color("rejected"), do: "border-red-200"
  defp bid_border_color(_), do: "border-gray-200"

  defp format_number(n) when is_integer(n), do: Number.to_string(n)
  defp format_number(n) when is_binary(n), do: n
  defp format_number(n), do: to_string(n)

  defp relative_time(datetime) do
    diff = DateTime.diff(DateTime.utc_now(), datetime, :second)

    cond do
      diff < 60 -> "just now"
      diff < 3600 -> "#{div(diff, 60)}m ago"
      diff < 86400 -> "#{div(diff, 3600)}h ago"
      true -> "#{div(diff, 86400)}d ago"
    end
  end
end

defmodule Number do
  def to_string(n) when is_integer(n) do
    n
    |> Integer.to_string()
    |> String.reverse()
    |> String.replace(~r/(\d{3})/, "\\1,")
    |> String.reverse()
    |> String.trim_leading(",")
  end

  def to_string(n), do: Kernel.to_string(n)
end
