defmodule FtwRealtime.Marketplace.LineItem do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "line_items" do
    field(:description, :string)
    field(:quantity, :float, default: 1.0)
    field(:unit, :string)
    field(:unit_price, :integer, default: 0)
    field(:total, :integer, default: 0)
    field(:category, :string)
    field(:sort_order, :integer, default: 0)

    belongs_to(:estimate, FtwRealtime.Marketplace.Estimate)

    timestamps(type: :utc_datetime)
  end

  def changeset(line_item, attrs) do
    line_item
    |> cast(attrs, [
      :description,
      :quantity,
      :unit,
      :unit_price,
      :total,
      :category,
      :sort_order,
      :estimate_id
    ])
    |> validate_required([:description])
    |> foreign_key_constraint(:estimate_id)
  end
end
