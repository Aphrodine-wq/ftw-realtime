defmodule FtwRealtime.Marketplace.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "users" do
    field(:email, :string)
    field(:name, :string)
    field(:role, Ecto.Enum, values: [:homeowner, :contractor, :admin])
    field(:phone, :string)
    field(:location, :string)
    field(:license_number, :string)
    field(:insurance_verified, :boolean, default: false)
    field(:rating, :float, default: 0.0)
    field(:jobs_completed, :integer, default: 0)
    field(:avatar_url, :string)
    field(:active, :boolean, default: true)
    field(:password_hash, :string)
    field(:password, :string, virtual: true)

    has_many(:posted_jobs, FtwRealtime.Marketplace.Job, foreign_key: :homeowner_id)
    has_many(:bids, FtwRealtime.Marketplace.Bid, foreign_key: :contractor_id)

    timestamps(type: :utc_datetime)
  end

  def changeset(user, attrs) do
    user
    |> cast(attrs, [
      :email,
      :name,
      :role,
      :phone,
      :location,
      :license_number,
      :insurance_verified,
      :rating,
      :jobs_completed,
      :avatar_url,
      :active
    ])
    |> validate_required([:email, :name, :role])
    |> unique_constraint(:email)
    |> validate_inclusion(:role, [:homeowner, :contractor, :admin])
  end

  def registration_changeset(user, attrs) do
    user
    |> changeset(attrs)
    |> cast(attrs, [:password])
    |> validate_required([:password])
    |> validate_length(:password, min: 8)
    |> hash_password()
  end

  def verify_password(%__MODULE__{password_hash: hash}, password) when is_binary(hash) do
    Argon2.verify_pass(password, hash)
  end

  def verify_password(_, _), do: Argon2.no_user_verify()

  defp hash_password(changeset) do
    case get_change(changeset, :password) do
      nil -> changeset
      password -> put_change(changeset, :password_hash, Argon2.hash_pwd_salt(password))
    end
  end
end
