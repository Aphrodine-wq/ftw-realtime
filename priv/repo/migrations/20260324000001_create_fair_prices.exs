defmodule FtwRealtime.Repo.Migrations.CreateFairPrices do
  use Ecto.Migration

  def change do
    create table(:fair_prices, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :category, :string, null: false
      add :zip_prefix, :string, null: false
      add :size, :string, null: false
      add :low, :integer, null: false
      add :high, :integer, null: false
      add :materials_pct, :float
      add :labor_pct, :float
      add :confidence, :string
      add :raw_response, :text

      timestamps(type: :utc_datetime)
    end

    create unique_index(:fair_prices, [:category, :zip_prefix, :size])
  end
end
