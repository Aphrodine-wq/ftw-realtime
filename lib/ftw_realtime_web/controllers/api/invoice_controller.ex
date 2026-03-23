defmodule FtwRealtimeWeb.Api.InvoiceController do
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

    invoices = Marketplace.list_invoices(conn.assigns.current_user_id, opts)

    json(conn, %{
      invoices: Enum.map(invoices, &serialize_invoice/1)
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_invoice(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Invoice not found"})

      invoice ->
        json(conn, %{invoice: serialize_invoice(invoice)})
    end
  end

  def create(conn, %{"invoice" => invoice_params}) do
    invoice_params = Map.put(invoice_params, "contractor_id", conn.assigns.current_user_id)

    case Marketplace.create_invoice(invoice_params) do
      {:ok, invoice} ->
        conn |> put_status(:created) |> json(%{invoice: serialize_invoice(invoice)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def update(conn, %{"id" => id, "invoice" => invoice_params}) do
    case Marketplace.update_invoice(id, invoice_params) do
      {:ok, invoice} ->
        json(conn, %{invoice: serialize_invoice(invoice)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Invoice not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  defp serialize_invoice(invoice) do
    %{
      id: invoice.id,
      invoice_number: invoice.invoice_number,
      amount: invoice.amount,
      status: invoice.status,
      due_date: invoice.due_date,
      paid_at: invoice.paid_at,
      notes: invoice.notes,
      estimate_id: invoice.estimate_id,
      project_id: invoice.project_id,
      contractor: serialize_user(invoice.contractor),
      client: serialize_client(invoice.client),
      created_at: invoice.inserted_at,
      updated_at: invoice.updated_at
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
