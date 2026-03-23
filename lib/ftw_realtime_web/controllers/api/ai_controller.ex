defmodule FtwRealtimeWeb.Api.AIController do
  use FtwRealtimeWeb, :controller

  def estimate(conn, %{"description" => description}) do
    case FtwRealtime.AI.estimate(description) do
      {:ok, content} ->
        # Try to parse as JSON, fall back to raw text
        case Jason.decode(content) do
          {:ok, parsed} ->
            conn |> put_status(:ok) |> json(%{estimate: parsed, raw: nil})

          {:error, _} ->
            conn |> put_status(:ok) |> json(%{estimate: nil, raw: content})
        end

      {:error, reason} ->
        conn
        |> put_status(:service_unavailable)
        |> json(%{error: "AI service unavailable", detail: inspect(reason)})
    end
  end

  def estimate(conn, _params) do
    conn |> put_status(:bad_request) |> json(%{error: "description is required"})
  end
end
