defmodule FtwRealtime.Marketplace.Job do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "jobs" do
    field(:title, :string)
    field(:description, :string)
    field(:category, :string)
    field(:budget_min, :integer)
    field(:budget_max, :integer)
    field(:location, :string)

    field(:status, Ecto.Enum,
      values: [:open, :bidding, :awarded, :in_progress, :completed, :disputed, :cancelled],
      default: :open
    )

    field(:bid_count, :integer, default: 0)

    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    has_many(:bids, FtwRealtime.Marketplace.Bid)

    timestamps(type: :utc_datetime)
  end

  def changeset(job, attrs) do
    job
    |> cast(attrs, [
      :title,
      :description,
      :category,
      :budget_min,
      :budget_max,
      :location,
      :status,
      :homeowner_id
    ])
    |> validate_required([:title, :description, :category, :location, :homeowner_id])
    |> validate_number(:budget_min, greater_than_or_equal_to: 0)
    |> validate_number(:budget_max, greater_than_or_equal_to: 0)
    |> foreign_key_constraint(:homeowner_id)
  end
end
