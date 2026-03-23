defmodule FtwRealtime.Auth do
  @moduledoc """
  JWT authentication. Signs and verifies tokens for API and WebSocket access.

  Tokens contain: user_id, email, role, exp.
  Signed with HS256 using the app's secret key base.
  """

  use Joken.Config

  @token_ttl 86_400

  @impl true
  def token_config do
    default_claims(default_exp: @token_ttl, iss: "ftw-realtime", aud: "ftw")
  end

  def generate_token(user) do
    claims = %{
      "user_id" => user.id,
      "email" => user.email,
      "role" => to_string(user.role)
    }

    generate_and_sign(claims, signer())
  end

  def verify_token(token) do
    case verify_and_validate(token, signer()) do
      {:ok, claims} -> {:ok, claims}
      {:error, reason} -> {:error, reason}
    end
  end

  defp signer do
    secret = Application.get_env(:ftw_realtime, FtwRealtimeWeb.Endpoint)[:secret_key_base]
    Joken.Signer.create("HS256", secret)
  end
end
