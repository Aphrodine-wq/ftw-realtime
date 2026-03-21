defmodule FtwRealtimeWeb.PageController do
  use FtwRealtimeWeb, :controller

  def home(conn, _params) do
    render(conn, :home)
  end
end
