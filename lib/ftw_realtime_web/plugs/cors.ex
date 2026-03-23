defmodule FtwRealtimeWeb.Plugs.CORS do
  @moduledoc """
  CORS plug. Reads allowed origins from config, falls back to * in dev.
  """
  import Plug.Conn

  @allowed_origins (if Mix.env() == :prod do
                      [
                        "https://fairtradeworker.com",
                        "https://www.fairtradeworker.com",
                        "https://fairtradeworker.vercel.app"
                      ]
                    else
                      ["*"]
                    end)

  def init(opts), do: opts

  def call(conn, _opts) do
    origin = get_req_header(conn, "origin") |> List.first() || ""

    allowed_origin =
      if "*" in @allowed_origins do
        "*"
      else
        if origin in @allowed_origins, do: origin, else: ""
      end

    conn
    |> put_resp_header("access-control-allow-origin", allowed_origin)
    |> put_resp_header("access-control-allow-methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
    |> put_resp_header("access-control-allow-headers", "content-type, authorization")
    |> handle_preflight()
  end

  defp handle_preflight(%{method: "OPTIONS"} = conn) do
    conn |> send_resp(204, "") |> halt()
  end

  defp handle_preflight(conn), do: conn
end
