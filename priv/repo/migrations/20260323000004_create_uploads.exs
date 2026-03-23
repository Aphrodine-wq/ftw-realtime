defmodule FtwRealtime.Repo.Migrations.CreateUploads do
  use Ecto.Migration

  def change do
    create table(:uploads, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :filename, :string, null: false
      add :content_type, :string, null: false
      add :size, :integer
      add :path, :string, null: false
      add :entity_type, :string, null: false
      add :entity_id, :binary_id, null: false
      add :uploader_id, references(:users, type: :binary_id, on_delete: :nothing), null: false

      timestamps(type: :utc_datetime)
    end

    create index(:uploads, [:entity_type, :entity_id])
    create index(:uploads, [:uploader_id])
  end
end
