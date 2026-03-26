defmodule FtwRealtime.Repo.Migrations.CreateFairRecords do
  use Ecto.Migration

  def change do
    create table(:fair_records, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :public_id, :string, null: false
      add :category, :string, null: false
      add :location_city, :string, null: false
      add :scope_summary, :text

      # Budget tracking (cents)
      add :estimated_budget, :integer, default: 0
      add :final_cost, :integer, default: 0
      add :budget_accuracy_pct, :float
      add :on_budget, :boolean, default: false

      # Timeline tracking
      add :estimated_end_date, :date
      add :actual_completion_date, :date
      add :on_time, :boolean, default: false

      # Quality snapshot
      add :quality_score_at_completion, :integer, default: 0
      add :avg_rating, :float, default: 0.0
      add :review_count, :integer, default: 0
      add :dispute_count, :integer, default: 0

      # Photos
      add :photos, {:array, :string}, default: []

      # Homeowner confirmation
      add :homeowner_confirmed, :boolean, default: false
      add :confirmed_at, :utc_datetime

      # Verification
      add :signature_hash, :string

      # Relationships
      add :project_id, references(:projects, type: :binary_id, on_delete: :restrict), null: false
      add :contractor_id, references(:users, type: :binary_id, on_delete: :restrict), null: false
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :restrict), null: false
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nilify_all)

      timestamps(type: :utc_datetime)
    end

    create unique_index(:fair_records, [:public_id])
    create unique_index(:fair_records, [:project_id])
    create index(:fair_records, [:contractor_id])
    create index(:fair_records, [:homeowner_id])
  end
end
