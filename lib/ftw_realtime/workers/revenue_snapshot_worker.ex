defmodule FtwRealtime.Workers.RevenueSnapshotWorker do
  @moduledoc "Daily midnight snapshot of revenue and marketplace metrics."
  use Oban.Worker, queue: :sync, max_attempts: 2

  @impl Oban.Worker
  def perform(_job) do
    yesterday = Date.utc_today() |> Date.add(-1)
    {:ok, _} = FtwRealtime.Ops.FairLedger.take_snapshot(yesterday)
    IO.puts("[RevenueSnapshot] Recorded snapshot for #{yesterday}")
    :ok
  end
end
