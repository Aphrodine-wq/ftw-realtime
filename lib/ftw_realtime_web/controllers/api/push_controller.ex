defmodule FtwRealtimeWeb.Api.PushController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def register(conn, %{"token" => token, "platform" => platform}) do
    user_id = conn.assigns.current_user_id

    case Marketplace.register_push_token(user_id, token, platform) do
      {:ok, push_token} ->
        conn
        |> put_status(:created)
        |> json(%{
          push_token: %{
            id: push_token.id,
            token: push_token.token,
            platform: push_token.platform
          }
        })

      {:error, changeset} ->
        conn
        |> put_status(:unprocessable_entity)
        |> json(%{errors: format_errors(changeset)})
    end
  end

  def register(conn, _params) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "Missing required fields: token, platform"})
  end

  def unregister(conn, %{"token" => token}) do
    case Marketplace.unregister_push_token(token) do
      {:ok, _} ->
        json(conn, %{ok: true})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Token not found"})
    end
  end

  def unregister(conn, _params) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "Missing required field: token"})
  end

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
