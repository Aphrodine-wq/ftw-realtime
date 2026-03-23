defmodule FtwRealtimeWeb.Api.AuthController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.{Auth, Marketplace}

  def login(conn, %{"email" => email, "password" => password}) do
    case Marketplace.authenticate_user(email, password) do
      {:ok, user} ->
        {:ok, token, _claims} = Auth.generate_token(user)

        conn
        |> put_status(:ok)
        |> json(%{
          token: token,
          user: %{
            id: user.id,
            email: user.email,
            name: user.name,
            role: user.role
          }
        })

      {:error, :invalid_credentials} ->
        conn |> put_status(:unauthorized) |> json(%{error: "Invalid email or password"})
    end
  end

  def login(conn, _params) do
    conn |> put_status(:bad_request) |> json(%{error: "Email and password required"})
  end

  def me(conn, _params) do
    case Marketplace.get_user(conn.assigns.current_user_id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "User not found"})

      user ->
        json(conn, %{
          user: %{
            id: user.id,
            email: user.email,
            name: user.name,
            role: user.role,
            location: user.location,
            rating: user.rating,
            jobs_completed: user.jobs_completed
          }
        })
    end
  end
end
