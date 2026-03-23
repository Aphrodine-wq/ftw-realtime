defmodule FtwRealtimeWeb.ChatChannel do
  use Phoenix.Channel

  alias FtwRealtime.Marketplace
  alias FtwRealtimeWeb.{Presence, RateLimiter}

  @impl true
  def join("chat:" <> conversation_id, _params, socket) do
    user_id = socket.assigns.user_id

    if Marketplace.conversation_participant?(conversation_id, user_id) do
      send(self(), {:after_join, conversation_id})
      {:ok, assign(socket, :conversation_id, conversation_id)}
    else
      {:error, %{reason: "unauthorized"}}
    end
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
    case RateLimiter.check(:typing, limit: 5, window: 3_000) do
      :rate_limited ->
        {:noreply, socket}

      :ok ->
        Presence.update(socket, socket.assigns.user_id, fn meta ->
          Map.put(meta, :typing, typing)
        end)

        {:noreply, socket}
    end
  end

  @impl true
  def handle_in("send_message", attrs, socket) do
    case RateLimiter.check(:message, limit: 10, window: 60_000) do
      :rate_limited ->
        {:reply, {:error, %{reason: "rate limited"}}, socket}

      :ok ->
        conversation_id = socket.assigns.conversation_id
        attrs = Map.put(attrs, "sender_id", socket.assigns.user_id)

        case Marketplace.send_message(conversation_id, attrs) do
          {:ok, message} -> {:reply, {:ok, serialize_message(message)}, socket}
          {:error, changeset} -> {:reply, {:error, %{errors: format_errors(changeset)}}, socket}
        end
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
