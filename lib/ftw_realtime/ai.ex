defmodule FtwRealtime.AI do
  @moduledoc "Client for ConstructionAI estimation model"

  @default_url "http://localhost:11434/api/chat"
  @model "constructionai:latest"
  @timeout 30_000

  def estimate(description, opts \\ []) do
    _ = opts
    url = System.get_env("CONSTRUCTIONAI_URL") || @default_url
    model = System.get_env("CONSTRUCTIONAI_MODEL") || @model

    body =
      Jason.encode!(%{
        model: model,
        messages: [
          %{role: "system", content: system_prompt()},
          %{role: "user", content: description}
        ],
        stream: false,
        options: %{temperature: 0.3}
      })

    case :httpc.request(
           :post,
           {to_charlist(url), [{~c"content-type", ~c"application/json"}], ~c"application/json",
            to_charlist(body)},
           [timeout: @timeout, connect_timeout: 5_000],
           []
         ) do
      {:ok, {{_, 200, _}, _, response_body}} ->
        case Jason.decode(to_string(response_body)) do
          {:ok, %{"message" => %{"content" => content}}} -> {:ok, content}
          {:ok, %{"choices" => [%{"message" => %{"content" => content}} | _]}} -> {:ok, content}
          {:ok, other} -> {:ok, inspect(other)}
          {:error, _} -> {:error, :invalid_response}
        end

      {:ok, {{_, status, _}, _, _}} ->
        {:error, {:http_error, status}}

      {:error, reason} ->
        {:error, reason}
    end
  end

  defp system_prompt do
    """
    You are ConstructionAI, an expert construction estimator. Given a project description,
    provide a detailed cost estimate with line items. Format your response as JSON with this structure:
    {"title": "...", "line_items": [{"description": "...", "quantity": 1, "unit": "each", "unit_price": 0, "category": "labor|material|equipment|subcontractor"}], "total": 0, "notes": "..."}
    All prices in cents (e.g., $150.00 = 15000). Be specific and realistic for the described scope.
    """
  end
end
