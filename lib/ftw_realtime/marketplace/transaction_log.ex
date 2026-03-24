defmodule FtwRealtime.Marketplace.TransactionLog do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  @types ~w(bid_accepted invoice_sent payment_received refund commission subscription)

  schema "transaction_logs" do
    field(:type, :string)
    field(:amount, :integer)
    field(:metadata, :map, default: %{})
    field(:recorded_at, :utc_datetime)

    belongs_to(:job, FtwRealtime.Marketplace.Job)
    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    belongs_to(:invoice, FtwRealtime.Marketplace.Invoice)

    timestamps(type: :utc_datetime)
  end

  def types, do: @types

  def changeset(log, attrs) do
    log
    |> cast(attrs, [
      :type,
      :amount,
      :job_id,
      :contractor_id,
      :homeowner_id,
      :invoice_id,
      :metadata,
      :recorded_at
    ])
    |> validate_required([:type, :amount, :recorded_at])
    |> validate_inclusion(:type, @types)
  end
end
