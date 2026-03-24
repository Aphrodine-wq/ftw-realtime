defmodule FtwRealtime.Repo.Migrations.AddQualityScoreToUsers do
  use Ecto.Migration

  def change do
    alter table(:users) do
      add :quality_score, :integer, default: 0
    end
  end
end
