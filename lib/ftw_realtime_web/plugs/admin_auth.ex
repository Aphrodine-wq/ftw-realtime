defmodule FtwRealtimeWeb.Plugs.AdminAuth do
  @moduledoc """
  Basic password auth for admin routes.
  Set ADMIN_PASSWORD env var. Stores auth in session.
  """
  import Plug.Conn
  import Phoenix.Controller

  def init(opts), do: opts

  def call(conn, _opts) do
    password = System.get_env("ADMIN_PASSWORD") || "faircommand"

    cond do
      get_session(conn, :admin_authenticated) == true ->
        conn

      conn.params["password"] == password ->
        conn
        |> put_session(:admin_authenticated, true)
        |> redirect(to: conn.request_path)
        |> halt()

      true ->
        conn
        |> put_resp_content_type("text/html")
        |> send_resp(200, login_html())
        |> halt()
    end
  end

  defp login_html do
    """
    <!DOCTYPE html>
    <html>
    <head><title>FairCommand</title></head>
    <body style="font-family: -apple-system, system-ui, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #FDFBF8; margin: 0;">
      <form method="get" style="background: white; border: 1px solid #E5E1DB; border-radius: 16px; padding: 32px; width: 320px; text-align: center;">
        <h1 style="font-size: 20px; font-weight: 700; color: #0F1419; margin: 0 0 8px 0;">FairCommand</h1>
        <p style="font-size: 13px; color: #9CA3AF; margin: 0 0 24px 0;">Admin access</p>
        <input name="password" type="password" placeholder="Password" autofocus
          style="width: 100%; padding: 10px 14px; border: 1px solid #E5E1DB; border-radius: 8px; font-size: 14px; box-sizing: border-box; margin-bottom: 12px;" />
        <button type="submit"
          style="width: 100%; padding: 10px; border: none; border-radius: 8px; background: #C41E3A; color: white; font-size: 14px; font-weight: 600; cursor: pointer;">
          Enter
        </button>
      </form>
    </body>
    </html>
    """
  end
end
