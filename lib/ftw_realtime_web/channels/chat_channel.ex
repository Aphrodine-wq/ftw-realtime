defmodule FtwRealtimeWeb.ChatChannel do
  @moduledoc """
  Real-time chat between contractor and homeowner.
  Clients join "chat:<conversation_id>" for instant messaging.
  """
  use Phoenix.Channel

  alias FtwRealtime.Marketplace

  @impl true
  def join("chat:" <> conversation_id, _params, socket) do
    send(self(), {:after_join, conversation_id})
    {:ok, assign(socket, :conversation_id, conversation_id)}
  end

  @impl true
  def handle_info({:after_join, conversation_id}, socket) do
    messages = Marketplace.list_messages(conversation_id)
    push(socket, "messages:list", %{messages: messages})
    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "chat:#{conversation_id}")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"message:new", message}, socket) do
    push(socket, "message:new", message)
    {:noreply, socket}
  end

  @impl true
  def handle_in("send_message", attrs, socket) do
    conversation_id = socket.assigns.conversation_id

    case Marketplace.send_message(conversation_id, attrs) do
      {:ok, message} -> {:reply, {:ok, message}, socket}
      {:error, reason} -> {:reply, {:error, %{reason: reason}}, socket}
    end
  end
end
