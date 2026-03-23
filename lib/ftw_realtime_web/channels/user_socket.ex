defmodule FtwRealtimeWeb.UserSocket do
  use Phoenix.Socket

  channel "jobs:*", FtwRealtimeWeb.JobChannel
  channel "job:*", FtwRealtimeWeb.BidChannel
  channel "chat:*", FtwRealtimeWeb.ChatChannel
  channel "user:*", FtwRealtimeWeb.NotificationChannel

  @impl true
  def connect(%{"token" => token}, socket, _connect_info) do
    case FtwRealtime.Auth.verify_token(token) do
      {:ok, claims} ->
        socket =
          socket
          |> assign(:user_id, claims["user_id"])
          |> assign(:email, claims["email"])
          |> assign(:role, claims["role"])

        {:ok, socket}

      {:error, _reason} ->
        :error
    end
  end

  def connect(_params, _socket, _connect_info), do: :error

  @impl true
  def id(socket), do: "user_socket:#{socket.assigns.user_id}"
end
