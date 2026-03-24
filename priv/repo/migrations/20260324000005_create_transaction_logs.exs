defmodule FtwRealtime.Repo.Migrations.CreateTransactionLogs do
  use Ecto.Migration

  def change do
    create table(:transaction_logs, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :type, :string, null: false
      add :amount, :integer, null: false
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nilify_all)
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nilify_all)
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :nilify_all)
      add :invoice_id, references(:invoices, type: :binary_id, on_delete: :nilify_all)
      add :metadata, :map, default: %{}
      add :recorded_at, :utc_datetime, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:transaction_logs, [:type])
    create index(:transaction_logs, [:job_id])
    create index(:transaction_logs, [:recorded_at])
  end
end
