defmodule FtwRealtimeWeb.Api.SettingsController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def show(conn, _params) do
    settings = Marketplace.get_settings(conn.assigns.current_user_id)
    json(conn, %{settings: serialize_settings(settings)})
  end

  def update(conn, %{"settings" => settings_params}) do
    case Marketplace.update_settings(conn.assigns.current_user_id, settings_params) do
      {:ok, settings} ->
        json(conn, %{settings: serialize_settings(settings)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  defp serialize_settings(settings) do
    %{
      notifications_email: settings.notifications_email,
      notifications_push: settings.notifications_push,
      notifications_sms: settings.notifications_sms,
      appearance_theme: settings.appearance_theme,
      language: settings.language,
      timezone: settings.timezone,
      privacy_profile_visible: settings.privacy_profile_visible,
      privacy_show_rating: settings.privacy_show_rating
    }
  end

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
