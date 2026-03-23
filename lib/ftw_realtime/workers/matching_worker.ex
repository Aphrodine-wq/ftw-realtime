defmodule FtwRealtime.Workers.MatchingWorker do
  use Oban.Worker, queue: :matching, max_attempts: 2

  @moduledoc "Matches new jobs to contractors based on category + location"

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"job_id" => job_id}}) do
    # TODO: Implement matching algorithm
    # 1. Get job category + location
    # 2. Find verified contractors matching category + service area
    # 3. Create notifications for matched contractors
    # 4. Broadcast to notification channels
    IO.puts("[MatchingWorker] Would match contractors for job #{job_id}")
    :ok
  end
end
