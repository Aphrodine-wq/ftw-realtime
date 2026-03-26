defmodule FtwRealtime.Repo.Migrations.AddCoordinates do
  use Ecto.Migration

  def change do
    alter table(:users) do
      add :latitude, :float
      add :longitude, :float
    end

    alter table(:jobs) do
      add :latitude, :float
      add :longitude, :float
    end

    create index(:users, [:latitude, :longitude])
    create index(:jobs, [:latitude, :longitude])
  end
end
