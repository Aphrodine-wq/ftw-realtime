defmodule FtwRealtime.AI.FairPriceEntry do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}

  schema "fair_prices" do
    field(:category, :string)
    field(:zip_prefix, :string)
    field(:size, :string)
    field(:low, :integer)
    field(:high, :integer)
    field(:materials_pct, :float)
    field(:labor_pct, :float)
    field(:confidence, :string)
    field(:raw_response, :string)

    timestamps(type: :utc_datetime)
  end

  def changeset(entry, attrs) do
    entry
    |> cast(attrs, [
      :category,
      :zip_prefix,
      :size,
      :low,
      :high,
      :materials_pct,
      :labor_pct,
      :confidence,
      :raw_response
    ])
    |> validate_required([:category, :zip_prefix, :size, :low, :high])
    |> unique_constraint([:category, :zip_prefix, :size])
  end
end
