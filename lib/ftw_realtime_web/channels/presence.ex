defmodule FtwRealtimeWeb.Presence do
  use Phoenix.Presence,
    otp_app: :ftw_realtime,
    pubsub_server: FtwRealtime.PubSub
end
