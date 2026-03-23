defmodule FtwRealtime.Marketplace.Conversation do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "conversations" do
    belongs_to(:job, FtwRealtime.Marketplace.Job)
    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    belongs_to(:contractor, FtwRealtime.Marketplace.User)

    has_many(:messages, FtwRealtime.Marketplace.Message)

    timestamps(type: :utc_datetime)
  end

  def changeset(conversation, attrs) do
    conversation
    |> cast(attrs, [:job_id, :homeowner_id, :contractor_id])
    |> validate_required([:job_id, :homeowner_id, :contractor_id])
    |> foreign_key_constraint(:job_id)
    |> foreign_key_constraint(:homeowner_id)
    |> foreign_key_constraint(:contractor_id)
    |> unique_constraint([:job_id, :homeowner_id, :contractor_id])
  end
end
