defmodule FtwRealtime.Workers.NotificationWorker do
  use Oban.Worker, queue: :notifications, max_attempts: 3

  alias FtwRealtime.Marketplace
  alias FtwRealtime.Push

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"type" => type, "user_id" => user_id} = args}) do
    title = args["title"] || "Notification"
    body = args["body"] || ""
    metadata = args["metadata"] || %{}

    attrs = %{
      type: type,
      title: title,
      body: body,
      user_id: user_id,
      metadata: metadata
    }

    case Marketplace.create_notification(attrs) do
      {:ok, _notification} ->
        # Also send push notification to all user devices
        Push.send_push(user_id, title, body, %{type: type, metadata: metadata})
        :ok

      {:error, reason} ->
        {:error, reason}
    end
  end
end
