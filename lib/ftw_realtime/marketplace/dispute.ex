defmodule FtwRealtime.Marketplace.Dispute do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @reasons ~w(scope_disagreement quality payment timeline communication other)
  @statuses ~w(open investigating resolved_contractor resolved_homeowner escalated closed)

  schema "disputes" do
    field(:reason, :string)
    field(:status, :string, default: "open")
    field(:description, :string)
    field(:resolution_notes, :string)
    field(:opened_at, :utc_datetime)
    field(:resolved_at, :utc_datetime)

    belongs_to(:job, FtwRealtime.Marketplace.Job)
    belongs_to(:opener, FtwRealtime.Marketplace.User, foreign_key: :opened_by)
    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    belongs_to(:resolver, FtwRealtime.Marketplace.User, foreign_key: :resolved_by)

    has_many(:evidence, FtwRealtime.Marketplace.DisputeEvidence)

    timestamps(type: :utc_datetime)
  end

  def reasons, do: @reasons
  def statuses, do: @statuses

  def changeset(dispute, attrs) do
    dispute
    |> cast(attrs, [
      :job_id,
      :opened_by,
      :contractor_id,
      :homeowner_id,
      :reason,
      :status,
      :description,
      :resolution_notes,
      :opened_at,
      :resolved_at,
      :resolved_by
    ])
    |> validate_required([
      :job_id,
      :opened_by,
      :contractor_id,
      :homeowner_id,
      :reason,
      :opened_at
    ])
    |> validate_inclusion(:reason, @reasons)
    |> validate_inclusion(:status, @statuses)
    |> foreign_key_constraint(:job_id)
  end
end
