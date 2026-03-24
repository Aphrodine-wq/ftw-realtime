defmodule FtwRealtime.Workers.VerificationExpiryWorker do
  @moduledoc "Daily check for expired contractor verifications."
  use Oban.Worker, queue: :sync, max_attempts: 1

  @impl Oban.Worker
  def perform(_job) do
    expired = FtwRealtime.Ops.FairTrust.check_expirations()
    if expired > 0, do: IO.puts("[VerificationExpiry] Expired #{expired} verifications")
    :ok
  end
end
