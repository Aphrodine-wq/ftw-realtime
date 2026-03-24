defmodule FtwRealtime.Repo.Migrations.CreateContentFlags do
  use Ecto.Migration

  def change do
    create table(:content_flags, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :entity_type, :string, null: false
      add :entity_id, :binary_id, null: false
      add :reason, :string, null: false
      add :flagged_by, references(:users, type: :binary_id, on_delete: :nilify_all)
      add :status, :string, null: false, default: "open"
      add :resolved_by, references(:users, type: :binary_id, on_delete: :nilify_all)
      add :resolved_at, :utc_datetime
      add :notes, :text

      timestamps(type: :utc_datetime)
    end

    create index(:content_flags, [:status])
    create index(:content_flags, [:entity_type, :entity_id])
  end
end
