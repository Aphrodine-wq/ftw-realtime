defmodule FtwRealtime.Marketplace.Review do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id

  schema "reviews" do
    field(:rating, :integer)
    field(:comment, :string)
    field(:response, :string)

    belongs_to(:reviewer, FtwRealtime.Marketplace.User)
    belongs_to(:reviewed, FtwRealtime.Marketplace.User)
    belongs_to(:job, FtwRealtime.Marketplace.Job)

    timestamps(type: :utc_datetime)
  end

  def changeset(review, attrs) do
    review
    |> cast(attrs, [:rating, :comment, :response, :reviewer_id, :reviewed_id, :job_id])
    |> validate_required([:rating, :reviewer_id, :reviewed_id])
    |> validate_inclusion(:rating, 1..5)
    |> foreign_key_constraint(:reviewer_id)
    |> foreign_key_constraint(:reviewed_id)
    |> foreign_key_constraint(:job_id)
    |> unique_constraint([:reviewer_id, :job_id])
  end
end
