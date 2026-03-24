defmodule FtwRealtime.Marketplace.Verification do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @steps ~w(license insurance background identity)
  @statuses ~w(pending approved rejected expired)

  schema "verifications" do
    field(:step, :string)
    field(:status, :string, default: "pending")
    field(:data, :map, default: %{})
    field(:reviewed_at, :utc_datetime)
    field(:expires_at, :utc_datetime)
    field(:notes, :string)

    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:reviewer, FtwRealtime.Marketplace.User, foreign_key: :reviewed_by)

    timestamps(type: :utc_datetime)
  end

  def steps, do: @steps
  def statuses, do: @statuses

  def changeset(verification, attrs) do
    verification
    |> cast(attrs, [
      :contractor_id,
      :step,
      :status,
      :data,
      :reviewed_by,
      :reviewed_at,
      :expires_at,
      :notes
    ])
    |> validate_required([:contractor_id, :step])
    |> validate_inclusion(:step, @steps)
    |> validate_inclusion(:status, @statuses)
    |> unique_constraint([:contractor_id, :step])
    |> foreign_key_constraint(:contractor_id)
  end
end
