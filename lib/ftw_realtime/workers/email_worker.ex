defmodule FtwRealtime.Workers.EmailWorker do
  use Oban.Worker, queue: :default, max_attempts: 5

  @moduledoc "Sends transactional emails (bid received, payment, etc)"

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"to" => _to, "template" => _template}}) do
    # TODO: Wire to email provider (Postmark, SES, etc)
    # For now, just log
    IO.puts("[EmailWorker] Would send email")
    :ok
  end
end
