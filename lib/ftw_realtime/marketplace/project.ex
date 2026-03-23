defmodule FtwRealtime.Marketplace.Project do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "projects" do
    field(:name, :string)
    field(:description, :string)

    field(:status, Ecto.Enum,
      values: [:planning, :active, :on_hold, :completed, :cancelled],
      default: :planning
    )

    field(:start_date, :date)
    field(:end_date, :date)
    field(:budget, :integer, default: 0)
    field(:spent, :integer, default: 0)

    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    belongs_to(:job, FtwRealtime.Marketplace.Job)

    timestamps(type: :utc_datetime)
  end

  def changeset(project, attrs) do
    project
    |> cast(attrs, [
      :name,
      :description,
      :status,
      :start_date,
      :end_date,
      :budget,
      :spent,
      :contractor_id,
      :homeowner_id,
      :job_id
    ])
    |> validate_required([:name, :contractor_id, :homeowner_id])
    |> foreign_key_constraint(:contractor_id)
    |> foreign_key_constraint(:homeowner_id)
    |> foreign_key_constraint(:job_id)
  end
end
