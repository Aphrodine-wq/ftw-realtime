defmodule FtwRealtimeWeb.Api.ClientController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, _params) do
    clients = Marketplace.list_clients(conn.assigns.current_user_id)

    json(conn, %{
      clients: Enum.map(clients, &serialize_client/1)
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_client(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Client not found"})

      client ->
        json(conn, %{client: serialize_client(client)})
    end
  end

  def create(conn, %{"client" => client_params}) do
    client_params = Map.put(client_params, "contractor_id", conn.assigns.current_user_id)

    case Marketplace.create_client(client_params) do
      {:ok, client} ->
        conn |> put_status(:created) |> json(%{client: serialize_client(client)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def update(conn, %{"id" => id, "client" => client_params}) do
    case Marketplace.update_client(id, client_params) do
      {:ok, client} ->
        json(conn, %{client: serialize_client(client)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Client not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def delete(conn, %{"id" => id}) do
    case Marketplace.delete_client(id) do
      {:ok, _client} ->
        conn |> put_status(:no_content) |> json(%{})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Client not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  defp serialize_client(client) do
    %{
      id: client.id,
      name: client.name,
      email: client.email,
      phone: client.phone,
      address: client.address,
      contractor_id: client.contractor_id,
      created_at: client.inserted_at,
      updated_at: client.updated_at
    }
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
