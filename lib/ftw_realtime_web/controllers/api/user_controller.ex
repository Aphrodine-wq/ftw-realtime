defmodule FtwRealtimeWeb.Api.UserController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def show(conn, %{"id" => id}) do
    case Marketplace.get_user(id) do
      nil -> conn |> put_status(:not_found) |> json(%{error: "User not found"})
      user -> json(conn, %{user: serialize_user(user)})
    end
  end

  def create(conn, %{"user" => user_params}) do
    case Marketplace.register_user(user_params) do
      {:ok, user} ->
        conn |> put_status(:created) |> json(%{user: serialize_user(user)})

      {:error, changeset} ->
        errors =
          Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
            Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
              opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
            end)
          end)

        conn |> put_status(:unprocessable_entity) |> json(%{errors: errors})
    end
  end

  defp serialize_user(user) do
    %{
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      phone: user.phone,
      location: user.location,
      license_number: user.license_number,
      insurance_verified: user.insurance_verified,
      rating: user.rating,
      jobs_completed: user.jobs_completed,
      avatar_url: user.avatar_url,
      active: user.active
    }
  end
end
