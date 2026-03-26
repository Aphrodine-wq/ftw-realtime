defmodule FtwRealtime.Push do
  @moduledoc """
  Sends push notifications via the Expo Push API.
  Looks up all push tokens for a user and sends to each device.
  """

  require Logger

  alias FtwRealtime.Repo
  alias FtwRealtime.Marketplace.PushToken

  import Ecto.Query

  @expo_push_url "https://exp.host/--/api/v2/push/send"

  @doc """
  Sends a push notification to all devices registered to `user_id`.
  Returns `:ok` on success or `{:error, reason}` on failure.
  """
  def send_push(user_id, title, body, data \\ %{}) do
    tokens =
      PushToken
      |> where([t], t.user_id == ^user_id)
      |> select([t], t.token)
      |> Repo.all()

    case tokens do
      [] ->
        Logger.debug("No push tokens for user #{user_id}, skipping push")
        :ok

      tokens ->
        messages =
          Enum.map(tokens, fn token ->
            %{
              to: token,
              title: title,
              body: body,
              data: data,
              sound: "default"
            }
          end)

        send_to_expo(messages)
    end
  end

  defp send_to_expo(messages) do
    headers = [
      {"Content-Type", "application/json"},
      {"Accept", "application/json"}
    ]

    headers =
      case System.get_env("EXPO_ACCESS_TOKEN") do
        nil -> headers
        token -> [{"Authorization", "Bearer #{token}"} | headers]
      end

    body = Jason.encode!(messages)

    case :httpc.request(
           :post,
           {~c"#{@expo_push_url}", Enum.map(headers, fn {k, v} -> {~c"#{k}", ~c"#{v}"} end),
            ~c"application/json", body},
           [{:timeout, 15_000}, {:connect_timeout, 5_000}],
           []
         ) do
      {:ok, {{_, status, _}, _headers, response_body}} when status in 200..299 ->
        Logger.info("Expo push sent: #{length(messages)} message(s), status #{status}")

        case Jason.decode(to_string(response_body)) do
          {:ok, %{"data" => data}} ->
            Enum.each(data, fn
              %{"status" => "error", "message" => msg} ->
                Logger.warning("Expo push error: #{msg}")

              _ ->
                :ok
            end)

          _ ->
            :ok
        end

        :ok

      {:ok, {{_, status, _}, _headers, response_body}} ->
        Logger.error("Expo push failed: status #{status}, body: #{response_body}")
        {:error, {:http_error, status}}

      {:error, reason} ->
        Logger.error("Expo push request failed: #{inspect(reason)}")
        {:error, reason}
    end
  end
end
