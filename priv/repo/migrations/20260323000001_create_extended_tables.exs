defmodule FtwRealtime.Repo.Migrations.CreateExtendedTables do
  use Ecto.Migration

  def change do
    create table(:clients, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string, null: false
      add :email, :string
      add :phone, :string
      add :address, :text
      add :notes, :text
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :user_id, references(:users, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:clients, [:contractor_id])

    create table(:estimates, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :title, :string, null: false
      add :description, :text
      add :total, :integer, default: 0
      add :status, :string, default: "draft"
      add :valid_until, :utc_datetime
      add :notes, :text
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :client_id, references(:clients, type: :binary_id, on_delete: :nothing)
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:estimates, [:contractor_id])
    create index(:estimates, [:client_id])
    create index(:estimates, [:status])

    create table(:line_items, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :description, :string, null: false
      add :quantity, :float, default: 1.0
      add :unit, :string
      add :unit_price, :integer, default: 0
      add :total, :integer, default: 0
      add :category, :string
      add :sort_order, :integer, default: 0
      add :estimate_id, references(:estimates, type: :binary_id, on_delete: :delete_all), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:line_items, [:estimate_id])

    create table(:projects, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string, null: false
      add :description, :text
      add :status, :string, default: "planning"
      add :start_date, :date
      add :end_date, :date
      add :budget, :integer, default: 0
      add :spent, :integer, default: 0
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :homeowner_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:projects, [:contractor_id])
    create index(:projects, [:homeowner_id])
    create index(:projects, [:status])

    create table(:invoices, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :invoice_number, :string, null: false
      add :amount, :integer, default: 0
      add :status, :string, default: "draft"
      add :due_date, :date
      add :paid_at, :utc_datetime
      add :notes, :text
      add :contractor_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :client_id, references(:clients, type: :binary_id, on_delete: :nothing)
      add :estimate_id, references(:estimates, type: :binary_id, on_delete: :nothing)
      add :project_id, references(:projects, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:invoices, [:contractor_id])
    create index(:invoices, [:client_id])
    create index(:invoices, [:status])

    create table(:notifications, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :type, :string, null: false
      add :title, :string, null: false
      add :body, :text
      add :read, :boolean, default: false
      add :metadata, :map
      add :user_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:notifications, [:user_id])
    create index(:notifications, [:read])
  end
end
