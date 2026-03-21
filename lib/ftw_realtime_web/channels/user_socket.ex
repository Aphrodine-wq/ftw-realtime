defmodule FtwRealtimeWeb.UserSocket do
  use Phoenix.Socket

  channel "jobs:*", FtwRealtimeWeb.JobChannel
  channel "job:*", FtwRealtimeWeb.BidChannel
  channel "chat:*", FtwRealtimeWeb.ChatChannel

  @impl true
  def connect(params, socket, _connect_info) do
    {:ok, assign(socket, :user_id, params["user_id"] || "anonymous")}
  end

  @impl true
  def id(socket), do: "user_socket:#{socket.assigns.user_id}"
end
