defmodule FtwRealtime.Marketplace.Message do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "messages" do
    field(:body, :string)

    belongs_to(:conversation, FtwRealtime.Marketplace.Conversation)
    belongs_to(:sender, FtwRealtime.Marketplace.User)

    timestamps(type: :utc_datetime)
  end

  def changeset(message, attrs) do
    message
    |> cast(attrs, [:body, :conversation_id, :sender_id])
    |> validate_required([:body, :conversation_id, :sender_id])
    |> foreign_key_constraint(:conversation_id)
    |> foreign_key_constraint(:sender_id)
  end
end
