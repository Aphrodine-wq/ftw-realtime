defmodule FtwRealtime.Marketplace.Upload do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @allowed_content_types ~w(image/jpeg image/png image/webp application/pdf)

  schema "uploads" do
    field :filename, :string
    field :content_type, :string
    field :size, :integer
    field :path, :string
    field :entity_type, :string
    field :entity_id, :binary_id

    belongs_to :uploader, FtwRealtime.Marketplace.User

    timestamps(type: :utc_datetime)
  end

  def changeset(upload, attrs) do
    upload
    |> cast(attrs, [:filename, :content_type, :size, :path, :entity_type, :entity_id, :uploader_id])
    |> validate_required([:filename, :content_type, :path, :entity_type, :entity_id, :uploader_id])
    |> validate_inclusion(:content_type, @allowed_content_types)
    |> validate_inclusion(:entity_type, ~w(job estimate user project))
    |> foreign_key_constraint(:uploader_id)
  end
end
