defmodule FtwRealtime.Repo do
  use Ecto.Repo,
    otp_app: :ftw_realtime,
    adapter: Ecto.Adapters.Postgres
end
