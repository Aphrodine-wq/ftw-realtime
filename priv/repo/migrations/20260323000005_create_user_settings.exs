defmodule FtwRealtime.Repo.Migrations.CreateUserSettings do
  use Ecto.Migration

  def change do
    create table(:user_settings, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :user_id, references(:users, type: :binary_id, on_delete: :delete_all), null: false
      add :notifications_email, :boolean, default: true
      add :notifications_push, :boolean, default: true
      add :notifications_sms, :boolean, default: false
      add :appearance_theme, :string, default: "light"
      add :language, :string, default: "en"
      add :timezone, :string, default: "America/Chicago"
      add :privacy_profile_visible, :boolean, default: true
      add :privacy_show_rating, :boolean, default: true

      timestamps(type: :utc_datetime)
    end

    create unique_index(:user_settings, [:user_id])
  end
end
