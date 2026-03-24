defmodule FtwRealtime.Marketplace.ContentFlag do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @entity_types ~w(job review message profile)
  @reasons ~w(spam scam inappropriate fake policy_violation)
  @statuses ~w(open reviewed dismissed actioned)

  schema "content_flags" do
    field(:entity_type, :string)
    field(:entity_id, :binary_id)
    field(:reason, :string)
    field(:status, :string, default: "open")
    field(:resolved_at, :utc_datetime)
    field(:notes, :string)

    belongs_to(:flagger, FtwRealtime.Marketplace.User, foreign_key: :flagged_by)
    belongs_to(:resolver, FtwRealtime.Marketplace.User, foreign_key: :resolved_by)

    timestamps(type: :utc_datetime)
  end

  def entity_types, do: @entity_types
  def reasons, do: @reasons
  def statuses, do: @statuses

  def changeset(flag, attrs) do
    flag
    |> cast(attrs, [
      :entity_type,
      :entity_id,
      :reason,
      :flagged_by,
      :status,
      :resolved_by,
      :resolved_at,
      :notes
    ])
    |> validate_required([:entity_type, :entity_id, :reason])
    |> validate_inclusion(:entity_type, @entity_types)
    |> validate_inclusion(:reason, @reasons)
    |> validate_inclusion(:status, @statuses)
  end
end
