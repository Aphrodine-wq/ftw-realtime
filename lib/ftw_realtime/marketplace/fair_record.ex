defmodule FtwRealtime.Marketplace.FairRecord do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "fair_records" do
    field(:public_id, :string)
    field(:category, :string)
    field(:location_city, :string)
    field(:scope_summary, :string)

    # Budget tracking (amounts in cents)
    field(:estimated_budget, :integer, default: 0)
    field(:final_cost, :integer, default: 0)
    field(:budget_accuracy_pct, :float)
    field(:on_budget, :boolean, default: false)

    # Timeline tracking
    field(:estimated_end_date, :date)
    field(:actual_completion_date, :date)
    field(:on_time, :boolean, default: false)

    # Quality snapshot at completion
    field(:quality_score_at_completion, :integer, default: 0)
    field(:avg_rating, :float, default: 0.0)
    field(:review_count, :integer, default: 0)
    field(:dispute_count, :integer, default: 0)

    # Photos (stored as list of upload paths)
    field(:photos, {:array, :string}, default: [])

    # Homeowner confirmation
    field(:homeowner_confirmed, :boolean, default: false)
    field(:confirmed_at, :utc_datetime)

    # Verification signature (Ed25519 hash for tamper-proofing)
    field(:signature_hash, :string)

    belongs_to(:project, FtwRealtime.Marketplace.Project)
    belongs_to(:contractor, FtwRealtime.Marketplace.User)
    belongs_to(:homeowner, FtwRealtime.Marketplace.User)
    belongs_to(:job, FtwRealtime.Marketplace.Job)

    timestamps(type: :utc_datetime)
  end

  @required_fields [:project_id, :contractor_id, :homeowner_id, :category, :location_city]
  @optional_fields [
    :public_id,
    :job_id,
    :scope_summary,
    :estimated_budget,
    :final_cost,
    :budget_accuracy_pct,
    :on_budget,
    :estimated_end_date,
    :actual_completion_date,
    :on_time,
    :quality_score_at_completion,
    :avg_rating,
    :review_count,
    :dispute_count,
    :photos,
    :homeowner_confirmed,
    :confirmed_at,
    :signature_hash
  ]

  def changeset(fair_record, attrs) do
    fair_record
    |> cast(attrs, @required_fields ++ @optional_fields)
    |> validate_required(@required_fields)
    |> maybe_generate_public_id()
    |> unique_constraint(:public_id)
    |> unique_constraint(:project_id)
    |> foreign_key_constraint(:project_id)
    |> foreign_key_constraint(:contractor_id)
    |> foreign_key_constraint(:homeowner_id)
    |> foreign_key_constraint(:job_id)
  end

  defp maybe_generate_public_id(changeset) do
    case get_field(changeset, :public_id) do
      nil -> put_change(changeset, :public_id, generate_public_id())
      _ -> changeset
    end
  end

  @doc "Generate a URL-safe public ID (e.g., FR-A3X9K2)."
  def generate_public_id do
    suffix =
      :crypto.strong_rand_bytes(4)
      |> Base.encode32(padding: false, case: :upper)
      |> binary_part(0, 6)

    "FR-#{suffix}"
  end
end
