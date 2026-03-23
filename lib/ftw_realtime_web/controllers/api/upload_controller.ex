defmodule FtwRealtimeWeb.Api.UploadController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  @upload_dir "priv/static/uploads"
  @max_size 10_485_760

  def create(conn, %{"file" => upload, "entity_type" => entity_type, "entity_id" => entity_id}) do
    %Plug.Upload{filename: filename, content_type: content_type, path: temp_path} = upload
    %{size: size} = File.stat!(temp_path)

    if size > @max_size do
      conn |> put_status(:request_entity_too_large) |> json(%{error: "File too large (max 10MB)"})
    else
      ext = Path.extname(filename)
      stored_name = "#{Ecto.UUID.generate()}#{ext}"
      dest = Path.join([@upload_dir, stored_name])
      File.mkdir_p!(Path.dirname(dest))
      File.cp!(temp_path, dest)

      attrs = %{
        filename: filename,
        content_type: content_type,
        size: size,
        path: stored_name,
        entity_type: entity_type,
        entity_id: entity_id,
        uploader_id: conn.assigns.current_user_id
      }

      case Marketplace.create_upload(attrs) do
        {:ok, upload_record} ->
          conn |> put_status(:created) |> json(%{upload: serialize_upload(upload_record)})

        {:error, changeset} ->
          File.rm(dest)
          conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
      end
    end
  end

  def index(conn, %{"entity_type" => entity_type, "entity_id" => entity_id}) do
    uploads = Marketplace.list_uploads(entity_type, entity_id)
    json(conn, %{uploads: Enum.map(uploads, &serialize_upload/1)})
  end

  def delete(conn, %{"id" => id}) do
    case Marketplace.delete_upload(id) do
      {:ok, upload} ->
        File.rm(Path.join(@upload_dir, upload.path))
        conn |> put_status(:no_content) |> send_resp(204, "")

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Upload not found"})
    end
  end

  defp serialize_upload(upload) do
    %{
      id: upload.id,
      filename: upload.filename,
      content_type: upload.content_type,
      size: upload.size,
      url: "/uploads/#{upload.path}",
      entity_type: upload.entity_type,
      entity_id: upload.entity_id,
      created_at: upload.inserted_at
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
