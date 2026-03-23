defmodule FtwRealtimeWeb.Api.ReviewController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, params) do
    user_id = params["for"] || conn.assigns.current_user_id

    reviews = Marketplace.list_reviews_for_user(user_id)

    json(conn, %{
      reviews: Enum.map(reviews, &serialize_review/1)
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_review(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Review not found"})

      review ->
        json(conn, %{review: serialize_review(review)})
    end
  end

  def create(conn, %{"review" => review_params}) do
    review_params = Map.put(review_params, "reviewer_id", conn.assigns.current_user_id)

    case Marketplace.create_review(review_params) do
      {:ok, review} ->
        conn |> put_status(:created) |> json(%{review: serialize_review(review)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def respond(conn, %{"id" => id, "response" => response_text}) do
    case Marketplace.get_review(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Review not found"})

      review ->
        if review.reviewed_id != conn.assigns.current_user_id do
          conn |> put_status(:forbidden) |> json(%{error: "Only the reviewed user can respond"})
        else
          case Marketplace.respond_to_review(id, response_text) do
            {:ok, updated_review} ->
              updated_review = Marketplace.get_review(updated_review.id)
              json(conn, %{review: serialize_review(updated_review)})

            {:error, changeset} ->
              conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
          end
        end
    end
  end

  defp serialize_review(review) do
    %{
      id: review.id,
      rating: review.rating,
      comment: review.comment,
      response: review.response,
      reviewer: serialize_user(review.reviewer),
      reviewed: serialize_user(review.reviewed),
      job_id: review.job_id,
      created_at: review.inserted_at
    }
  end

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name, role: user.role}
  end

  defp serialize_user(_), do: nil

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
