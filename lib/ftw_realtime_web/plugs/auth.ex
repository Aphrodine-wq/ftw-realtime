defmodule FtwRealtimeWeb.Plugs.Auth do
  @moduledoc """
  Plug that verifies JWT from the Authorization header.
  Sets conn.assigns.current_user_id, .current_email, .current_role on success.
  Returns 401 on failure.
  """
  import Plug.Conn

  alias FtwRealtime.Auth

  def init(opts), do: opts

  def call(conn, _opts) do
    with ["Bearer " <> token] <- get_req_header(conn, "authorization"),
         {:ok, claims} <- Auth.verify_token(token) do
      conn
      |> assign(:current_user_id, claims["user_id"])
      |> assign(:current_email, claims["email"])
      |> assign(:current_role, claims["role"])
    else
      _ ->
        conn
        |> put_status(:unauthorized)
        |> Phoenix.Controller.json(%{error: "Invalid or missing token"})
        |> halt()
    end
  end
end
