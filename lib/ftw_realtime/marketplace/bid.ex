defmodule FtwRealtime.Marketplace.Bid do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "bids" do
    field(:amount, :integer)
    field(:message, :string)
    field(:timeline, :string)

    field(:status, Ecto.Enum,
      values: [:pending, :accepted, :rejected, :withdrawn],
      default: :pending
    )

    belongs_to(:job, FtwRealtime.Marketplace.Job)
    belongs_to(:contractor, FtwRealtime.Marketplace.User)

    timestamps(type: :utc_datetime)
  end

  def changeset(bid, attrs) do
    bid
    |> cast(attrs, [:amount, :message, :timeline, :status, :job_id, :contractor_id])
    |> validate_required([:amount, :job_id, :contractor_id])
    |> validate_number(:amount, greater_than: 0)
    |> foreign_key_constraint(:job_id)
    |> foreign_key_constraint(:contractor_id)
  end
end
