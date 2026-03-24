defmodule FtwRealtime.Workers.FairScopeCleanupWorker do
  @moduledoc """
  Oban worker that cleans up expired FairScope cache entries.
  Runs daily to prevent unbounded ETS memory growth.
  """
  use Oban.Worker, queue: :sync, max_attempts: 1

  @impl Oban.Worker
  def perform(_job) do
    removed = FtwRealtime.AI.FairScope.cleanup()
    IO.puts("[FairScopeCleanup] Removed #{removed} expired entries")
    :ok
  end
end
