defmodule FtwRealtime.Storage do
  @moduledoc """
  File storage abstraction. Uses S3-compatible storage (R2, S3, MinIO) in
  production and local disk in development.

  Set these env vars for S3:
    STORAGE_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION
    Optional: STORAGE_ENDPOINT (for R2/MinIO)
  """

  @local_dir "priv/static/uploads"

  def put(stored_name, source_path) do
    if s3_configured?() do
      put_s3(stored_name, source_path)
    else
      put_local(stored_name, source_path)
    end
  end

  def delete(stored_name) do
    if s3_configured?() do
      delete_s3(stored_name)
    else
      delete_local(stored_name)
    end
  end

  def url(stored_name) do
    if s3_configured?() do
      bucket = System.get_env("STORAGE_BUCKET")
      endpoint = System.get_env("STORAGE_ENDPOINT", "https://s3.#{System.get_env("AWS_REGION", "us-east-1")}.amazonaws.com")
      "#{endpoint}/#{bucket}/#{stored_name}"
    else
      "/uploads/#{stored_name}"
    end
  end

  defp s3_configured? do
    System.get_env("STORAGE_BUCKET") != nil and
      System.get_env("AWS_ACCESS_KEY_ID") != nil
  end

  # --- S3 ---

  defp put_s3(stored_name, source_path) do
    bucket = System.get_env("STORAGE_BUCKET")
    body = File.read!(source_path)

    # Using raw HTTP — no ExAws dep needed
    # For production, add {:ex_aws, "~> 2.5"} and {:ex_aws_s3, "~> 2.5"} to deps
    # and replace this with ExAws.S3.put_object/3
    endpoint = System.get_env("STORAGE_ENDPOINT", "https://s3.#{System.get_env("AWS_REGION", "us-east-1")}.amazonaws.com")
    url = "#{endpoint}/#{bucket}/#{stored_name}"

    case :httpc.request(
           :put,
           {String.to_charlist(url), [], ~c"application/octet-stream", body},
           [],
           []
         ) do
      {:ok, {{_, status, _}, _, _}} when status in 200..299 -> :ok
      error -> {:error, error}
    end
  end

  defp delete_s3(stored_name) do
    bucket = System.get_env("STORAGE_BUCKET")
    endpoint = System.get_env("STORAGE_ENDPOINT", "https://s3.#{System.get_env("AWS_REGION", "us-east-1")}.amazonaws.com")
    url = "#{endpoint}/#{bucket}/#{stored_name}"

    :httpc.request(:delete, {String.to_charlist(url), []}, [], [])
    :ok
  end

  # --- Local ---

  defp put_local(stored_name, source_path) do
    dest = Path.join(@local_dir, stored_name)
    File.mkdir_p!(Path.dirname(dest))
    File.cp!(source_path, dest)
    :ok
  end

  defp delete_local(stored_name) do
    File.rm(Path.join(@local_dir, stored_name))
    :ok
  end
end
