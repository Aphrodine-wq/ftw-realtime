defmodule FtwRealtime.Repo.Migrations.CreateDisputes do
  use Ecto.Migration

  def change do
    create table(:disputes, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :job_id, references(:jobs, type: :binary_id, on_delete: :restrict), null: false
      add :opened_by, references(:users, type: :binary_id, on_delete: :restrict), null: false
      add :contractor_id, references(:users, type: :binary_id, on_delete: :restrict), null: false
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :restrict), null: false
      add :reason, :string, null: false
      add :status, :string, null: false, default: "open"
      add :description, :text
      add :resolution_notes, :text
      add :opened_at, :utc_datetime, null: false
      add :resolved_at, :utc_datetime
      add :resolved_by, references(:users, type: :binary_id, on_delete: :nilify_all)

      timestamps(type: :utc_datetime)
    end

    create index(:disputes, [:status])
    create index(:disputes, [:job_id])

    create table(:dispute_evidence, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :dispute_id, references(:disputes, type: :binary_id, on_delete: :delete_all), null: false
      add :submitted_by, references(:users, type: :binary_id, on_delete: :nilify_all), null: false
      add :type, :string, null: false
      add :content, :text, null: false
      add :submitted_at, :utc_datetime, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:dispute_evidence, [:dispute_id])

    create table(:revenue_snapshots, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :date, :date, null: false
      add :total_revenue, :integer, default: 0
      add :commission_revenue, :integer, default: 0
      add :jobs_completed, :integer, default: 0
      add :bids_placed, :integer, default: 0
      add :users_signed_up, :integer, default: 0
      add :disputes_opened, :integer, default: 0
      add :breakdown, :map, default: %{}

      timestamps(type: :utc_datetime)
    end

    create unique_index(:revenue_snapshots, [:date])
  end
end
