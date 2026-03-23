defmodule FtwRealtimeWeb.ChatChannel do
  use Phoenix.Channel

  alias FtwRealtime.Marketplace
  alias FtwRealtimeWeb.Presence

  @impl true
  def join("chat:" <> conversation_id, _params, socket) do
    send(self(), {:after_join, conversation_id})
    {:ok, assign(socket, :conversation_id, conversation_id)}
  end

  @impl true
  def handle_info({:after_join, conversation_id}, socket) do
    messages = Marketplace.list_messages(conversation_id)

    {:ok, _} =
      Presence.track(socket, socket.assigns.user_id, %{
        typing: false,
        online_at: System.system_time(:second)
      })

    push(socket, "presence_state", Presence.list(socket))

    push(socket, "messages:list", %{
      messages: Enum.map(messages, &serialize_message/1)
    })

    Phoenix.PubSub.subscribe(FtwRealtime.PubSub, "chat:#{conversation_id}")
    {:noreply, socket}
  end

  @impl true
  def handle_info({"message:new", message_data}, socket) do
    push(socket, "message:new", message_data)
    {:noreply, socket}
  end

  @impl true
  def handle_in("typing", %{"typing" => typing}, socket) do
    Presence.update(socket, socket.assigns.user_id, fn meta ->
      Map.put(meta, :typing, typing)
    end)

    {:noreply, socket}
  end

  @impl true
  def handle_in("send_message", attrs, socket) do
    conversation_id = socket.assigns.conversation_id
    attrs = Map.put(attrs, "sender_id", socket.assigns.user_id)

    case Marketplace.send_message(conversation_id, attrs) do
      {:ok, message} -> {:reply, {:ok, serialize_message(message)}, socket}
      {:error, changeset} -> {:reply, {:error, %{errors: format_errors(changeset)}}, socket}
    end
  end

  defp serialize_message(message) do
    %{
      id: message.id,
      conversation_id: message.conversation_id,
      body: message.body,
      sender_id: message.sender_id,
      sent_at: message.inserted_at
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
