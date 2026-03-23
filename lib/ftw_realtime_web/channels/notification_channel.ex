defmodule FtwRealtimeWeb.NotificationChannel do
  @moduledoc """
  User-specific notifications. Clients join "user:<user_id>" to receive
  bid updates, messages, and job status changes directed at them.
  """
  use Phoenix.Channel

  alias FtwRealtimeWeb.Presence

  @impl true
  def join("user:" <> user_id, _params, socket) do
    if socket.assigns.user_id == user_id do
      send(self(), :after_join)
      {:ok, socket}
    else
      {:error, %{reason: "unauthorized"}}
    end
  end

  @impl true
  def handle_info(:after_join, socket) do
    {:ok, _} =
      Presence.track(socket, socket.assigns.user_id, %{
        online_at: System.system_time(:second),
        status: "online"
      })

    push(socket, "presence_state", Presence.list(socket))
    {:noreply, socket}
  end

  @impl true
  def handle_info({"notification", payload}, socket) do
    push(socket, "notification", payload)
    {:noreply, socket}
  end
end
