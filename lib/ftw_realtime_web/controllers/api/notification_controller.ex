defmodule FtwRealtimeWeb.Api.NotificationController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, params) do
    limit =
      case Integer.parse(params["limit"] || "") do
        {n, _} when n > 0 -> n
        _ -> 20
      end

    opts = [
      limit: limit,
      after: params["after"]
    ]

    notifications = Marketplace.list_notifications(conn.assigns.current_user_id, opts)

    json(conn, %{
      notifications: Enum.map(notifications, &serialize_notification/1)
    })
  end

  def mark_read(conn, %{"id" => id}) do
    case Marketplace.mark_read(id) do
      {:ok, notification} ->
        json(conn, %{notification: serialize_notification(notification)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Notification not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def mark_all_read(conn, _params) do
    {count, _} = Marketplace.mark_all_read(conn.assigns.current_user_id)

    json(conn, %{marked_read: count})
  end

  defp serialize_notification(notification) do
    %{
      id: notification.id,
      type: notification.type,
      title: notification.title,
      body: notification.body,
      read: notification.read,
      metadata: notification.metadata,
      created_at: notification.inserted_at
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
