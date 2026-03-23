defmodule FtwRealtime.Marketplace.UserSetting do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "user_settings" do
    field :notifications_email, :boolean, default: true
    field :notifications_push, :boolean, default: true
    field :notifications_sms, :boolean, default: false
    field :appearance_theme, :string, default: "light"
    field :language, :string, default: "en"
    field :timezone, :string, default: "America/Chicago"
    field :privacy_profile_visible, :boolean, default: true
    field :privacy_show_rating, :boolean, default: true

    belongs_to :user, FtwRealtime.Marketplace.User

    timestamps(type: :utc_datetime)
  end

  def changeset(setting, attrs) do
    setting
    |> cast(attrs, [
      :user_id,
      :notifications_email,
      :notifications_push,
      :notifications_sms,
      :appearance_theme,
      :language,
      :timezone,
      :privacy_profile_visible,
      :privacy_show_rating
    ])
    |> validate_required([:user_id])
    |> validate_inclusion(:appearance_theme, ~w(light dark system))
    |> unique_constraint(:user_id)
    |> foreign_key_constraint(:user_id)
  end
end
