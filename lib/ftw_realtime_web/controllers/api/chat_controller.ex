defmodule FtwRealtimeWeb.Api.ChatController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, %{"conversation_id" => conversation_id}) do
    messages = Marketplace.list_messages(conversation_id)
    json(conn, %{messages: messages})
  end

  def create(conn, %{"conversation_id" => conversation_id, "message" => message_params}) do
    case Marketplace.send_message(conversation_id, message_params) do
      {:ok, message} -> conn |> put_status(:created) |> json(%{message: message})
      {:error, reason} -> conn |> put_status(:unprocessable_entity) |> json(%{error: reason})
    end
  end
end
