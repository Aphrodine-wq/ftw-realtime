defmodule FtwRealtimeWeb.Api.EstimateController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, params) do
    limit =
      case Integer.parse(params["limit"] || "") do
        {n, _} when n > 0 -> n
        _ -> 20
      end

    opts = [
      status: params["status"],
      limit: limit,
      after: params["after"]
    ]

    estimates = Marketplace.list_estimates(conn.assigns.current_user_id, opts)

    json(conn, %{
      estimates: Enum.map(estimates, &serialize_estimate/1)
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_estimate(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Estimate not found"})

      estimate ->
        json(conn, %{
          estimate: serialize_estimate(estimate),
          line_items: Enum.map(estimate.line_items || [], &serialize_line_item/1)
        })
    end
  end

  def create(conn, %{"estimate" => estimate_params}) do
    estimate_params = Map.put(estimate_params, "contractor_id", conn.assigns.current_user_id)

    case Marketplace.create_estimate(estimate_params) do
      {:ok, estimate} ->
        conn |> put_status(:created) |> json(%{estimate: serialize_estimate(estimate)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def update(conn, %{"id" => id, "estimate" => estimate_params}) do
    case Marketplace.update_estimate(id, estimate_params) do
      {:ok, estimate} ->
        json(conn, %{estimate: serialize_estimate(estimate)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Estimate not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def delete(conn, %{"id" => id}) do
    case Marketplace.delete_estimate(id) do
      {:ok, _estimate} ->
        conn |> put_status(:no_content) |> json(%{})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Estimate not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  defp serialize_estimate(estimate) do
    %{
      id: estimate.id,
      title: estimate.title,
      description: estimate.description,
      total: estimate.total,
      status: estimate.status,
      job_id: estimate.job_id,
      contractor: serialize_user(estimate.contractor),
      client: serialize_client(estimate.client),
      created_at: estimate.inserted_at,
      updated_at: estimate.updated_at
    }
  end

  defp serialize_line_item(item) do
    %{
      id: item.id,
      description: item.description,
      quantity: item.quantity,
      unit_price: item.unit_price,
      total: item.total
    }
  end

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name, role: user.role}
  end

  defp serialize_user(_), do: nil

  defp serialize_client(%{id: _} = client) do
    %{id: client.id, name: client.name, email: client.email}
  end

  defp serialize_client(_), do: nil

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
