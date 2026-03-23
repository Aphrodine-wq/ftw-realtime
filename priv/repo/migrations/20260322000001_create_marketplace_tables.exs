defmodule FtwRealtime.Repo.Migrations.CreateMarketplaceTables do
  use Ecto.Migration

  def change do
    create table(:users, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :email, :string, null: false
      add :name, :string, null: false
      add :role, :string, null: false, default: "homeowner"
      add :phone, :string
      add :location, :string
      add :license_number, :string
      add :insurance_verified, :boolean, default: false
      add :rating, :float, default: 0.0
      add :jobs_completed, :integer, default: 0
      add :avatar_url, :string
      add :active, :boolean, default: true

      timestamps(type: :utc_datetime)
    end

    create unique_index(:users, [:email])
    create index(:users, [:role])

    create table(:jobs, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :title, :string, null: false
      add :description, :text, null: false
      add :category, :string, null: false
      add :budget_min, :integer, default: 0
      add :budget_max, :integer, default: 0
      add :location, :string, null: false
      add :status, :string, null: false, default: "open"
      add :bid_count, :integer, default: 0
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:jobs, [:homeowner_id])
    create index(:jobs, [:status])
    create index(:jobs, [:category])

    create table(:bids, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :amount, :integer, null: false
      add :message, :text
      add :timeline, :string
      add :status, :string, null: false, default: "pending"
      add :job_id, references(:jobs, type: :binary_id, on_delete: :delete_all), null: false
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:bids, [:job_id])
    create index(:bids, [:contractor_id])
    create unique_index(:bids, [:job_id, :contractor_id])

    create table(:conversations, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nothing), null: false
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:conversations, [:job_id])
    create index(:conversations, [:homeowner_id])
    create index(:conversations, [:contractor_id])
    create unique_index(:conversations, [:job_id, :homeowner_id, :contractor_id])

    create table(:messages, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :body, :text, null: false
      add :conversation_id, references(:conversations, type: :binary_id, on_delete: :delete_all), null: false
      add :sender_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:messages, [:conversation_id])
    create index(:messages, [:sender_id])
  end
end
