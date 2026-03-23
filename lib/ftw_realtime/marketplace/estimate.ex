defmodule FtwRealtime.Marketplace.Estimate do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "estimates" do
    field(:title, :string)
    field(:description, :string)
    field(:total, :integer, default: 0)

    field(:status, Ecto.Enum,
      values: [:draft, :sent, :viewed, :accepted, :declined, :expired],
      default: :draft
    )

    field(:valid_until, :utc_datetime)
    field(:notes, :string)

    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:client, FtwRealtime.Marketplace.Client)
    belongs_to(:job, FtwRealtime.Marketplace.Job)

    has_many(:line_items, FtwRealtime.Marketplace.LineItem)

    timestamps(type: :utc_datetime)
  end

  def changeset(estimate, attrs) do
    estimate
    |> cast(attrs, [
      :title,
      :description,
      :total,
      :status,
      :valid_until,
      :notes,
      :contractor_id,
      :client_id,
      :job_id
    ])
    |> validate_required([:title, :contractor_id])
    |> foreign_key_constraint(:contractor_id)
    |> foreign_key_constraint(:client_id)
    |> foreign_key_constraint(:job_id)
  end
end
