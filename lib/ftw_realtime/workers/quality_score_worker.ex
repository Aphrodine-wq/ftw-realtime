defmodule FtwRealtime.Workers.QualityScoreWorker do
  @moduledoc "Weekly recomputation of contractor quality scores."
  use Oban.Worker, queue: :sync, max_attempts: 1

  alias FtwRealtime.Repo
  alias FtwRealtime.Marketplace.User

  import Ecto.Query

  @impl Oban.Worker
  def perform(_job) do
    contractor_ids =
      from(u in User,
        where: u.role == :contractor and u.jobs_completed > 0,
        select: u.id
      )
      |> Repo.all()

    Enum.each(contractor_ids, &FtwRealtime.Ops.FairTrust.compute_quality_score/1)
    IO.puts("[QualityScore] Recomputed scores for #{length(contractor_ids)} contractors")
    :ok
  end
end
