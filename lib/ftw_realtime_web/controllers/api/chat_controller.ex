defmodule FtwRealtimeWeb.Api.ChatController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, %{"conversation_id" => conversation_id}) do
    messages = Marketplace.list_messages(conversation_id)
    json(conn, %{messages: Enum.map(messages, &serialize_message/1)})
  end

  def create(conn, %{"conversation_id" => conversation_id, "message" => message_params}) do
    message_params = Map.put(message_params, "sender_id", conn.assigns.current_user_id)

    case Marketplace.send_message(conversation_id, message_params) do
      {:ok, message} ->
        conn |> put_status(:created) |> json(%{message: serialize_message(message)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
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

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name}
  end

  defp serialize_user(_), do: nil

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
