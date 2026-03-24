defmodule FtwRealtime.Marketplace.DisputeEvidence do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @types ~w(photo message document note)

  schema "dispute_evidence" do
    field(:type, :string)
    field(:content, :string)
    field(:submitted_at, :utc_datetime)

    belongs_to(:dispute, FtwRealtime.Marketplace.Dispute)
    belongs_to(:submitter, FtwRealtime.Marketplace.User, foreign_key: :submitted_by)

    timestamps(type: :utc_datetime)
  end

  def changeset(evidence, attrs) do
    evidence
    |> cast(attrs, [:dispute_id, :submitted_by, :type, :content, :submitted_at])
    |> validate_required([:dispute_id, :submitted_by, :type, :content, :submitted_at])
    |> validate_inclusion(:type, @types)
    |> foreign_key_constraint(:dispute_id)
  end
end
