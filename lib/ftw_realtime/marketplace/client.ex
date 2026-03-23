defmodule FtwRealtime.Marketplace.Client do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "clients" do
    field(:name, :string)
    field(:email, :string)
    field(:phone, :string)
    field(:address, :string)
    field(:notes, :string)

    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:user, FtwRealtime.Marketplace.User)

    has_many(:estimates, FtwRealtime.Marketplace.Estimate)
    has_many(:invoices, FtwRealtime.Marketplace.Invoice)

    timestamps(type: :utc_datetime)
  end

  def changeset(client, attrs) do
    client
    |> cast(attrs, [:name, :email, :phone, :address, :notes, :contractor_id, :user_id])
    |> validate_required([:name, :contractor_id])
    |> foreign_key_constraint(:contractor_id)
    |> foreign_key_constraint(:user_id)
  end
end
