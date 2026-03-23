defmodule FtwRealtime.Workers.NotificationWorker do
  use Oban.Worker, queue: :notifications, max_attempts: 3

  alias FtwRealtime.Marketplace

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"type" => type, "user_id" => user_id} = args}) do
    attrs = %{
      type: type,
      title: args["title"] || "Notification",
      body: args["body"] || "",
      user_id: user_id,
      metadata: args["metadata"] || %{}
    }

    case Marketplace.create_notification(attrs) do
      {:ok, _} -> :ok
      {:error, reason} -> {:error, reason}
    end
  end
end
