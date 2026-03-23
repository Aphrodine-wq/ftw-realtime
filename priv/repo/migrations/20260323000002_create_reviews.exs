defmodule FtwRealtime.Repo.Migrations.CreateReviews do
  use Ecto.Migration

  def change do
    create table(:reviews, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :rating, :integer, null: false
      add :comment, :text
      add :response, :text
      add :reviewer_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :reviewed_id, references(:users, type: :binary_id, on_delete: :nothing), null: false
      add :job_id, references(:jobs, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:reviews, [:reviewer_id])
    create index(:reviews, [:reviewed_id])
    create index(:reviews, [:job_id])
    create unique_index(:reviews, [:reviewer_id, :job_id])
  end
end
