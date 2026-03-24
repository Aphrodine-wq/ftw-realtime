defmodule FtwRealtime.Marketplace.RevenueSnapshot do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}

  schema "revenue_snapshots" do
    field(:date, :date)
    field(:total_revenue, :integer, default: 0)
    field(:commission_revenue, :integer, default: 0)
    field(:jobs_completed, :integer, default: 0)
    field(:bids_placed, :integer, default: 0)
    field(:users_signed_up, :integer, default: 0)
    field(:disputes_opened, :integer, default: 0)
    field(:breakdown, :map, default: %{})

    timestamps(type: :utc_datetime)
  end

  def changeset(snapshot, attrs) do
    snapshot
    |> cast(attrs, [
      :date,
      :total_revenue,
      :commission_revenue,
      :jobs_completed,
      :bids_placed,
      :users_signed_up,
      :disputes_opened,
      :breakdown
    ])
    |> validate_required([:date])
    |> unique_constraint(:date)
  end
end
