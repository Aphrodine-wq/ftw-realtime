defmodule FtwRealtime.Marketplace.Invoice do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "invoices" do
    field(:invoice_number, :string)
    field(:amount, :integer, default: 0)

    field(:status, Ecto.Enum,
      values: [:draft, :sent, :paid, :overdue, :cancelled],
      default: :draft
    )

    field(:due_date, :date)
    field(:paid_at, :utc_datetime)
    field(:notes, :string)

    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:client, FtwRealtime.Marketplace.Client)
    belongs_to(:estimate, FtwRealtime.Marketplace.Estimate)
    belongs_to(:project, FtwRealtime.Marketplace.Project)

    timestamps(type: :utc_datetime)
  end

  def changeset(invoice, attrs) do
    invoice
    |> cast(attrs, [
      :invoice_number,
      :amount,
      :status,
      :due_date,
      :paid_at,
      :notes,
      :contractor_id,
      :client_id,
      :estimate_id,
      :project_id
    ])
    |> validate_required([:invoice_number, :amount, :contractor_id])
    |> foreign_key_constraint(:contractor_id)
    |> foreign_key_constraint(:client_id)
    |> foreign_key_constraint(:estimate_id)
    |> foreign_key_constraint(:project_id)
  end
end
