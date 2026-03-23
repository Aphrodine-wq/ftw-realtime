defmodule FtwRealtime.Marketplace.Notification do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "notifications" do
    field(:type, :string)
    field(:title, :string)
    field(:body, :string)
    field(:read, :boolean, default: false)
    field(:metadata, :map)

    belongs_to(:user, FtwRealtime.Marketplace.User)

    timestamps(type: :utc_datetime)
  end

  def changeset(notification, attrs) do
    notification
    |> cast(attrs, [:type, :title, :body, :read, :metadata, :user_id])
    |> validate_required([:type, :title, :user_id])
    |> foreign_key_constraint(:user_id)
  end
end
