defmodule FtwRealtimeWeb.Api.ProjectController do
  use FtwRealtimeWeb, :controller

  alias FtwRealtime.Marketplace

  def index(conn, params) do
    limit =
      case Integer.parse(params["limit"] || "") do
        {n, _} when n > 0 -> n
        _ -> 20
      end

    opts = [
      status: params["status"],
      limit: limit,
      after: params["after"]
    ]

    projects = Marketplace.list_projects(conn.assigns.current_user_id, opts)

    json(conn, %{
      projects: Enum.map(projects, &serialize_project/1)
    })
  end

  def show(conn, %{"id" => id}) do
    case Marketplace.get_project(id) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "Project not found"})

      project ->
        json(conn, %{project: serialize_project(project)})
    end
  end

  def create(conn, %{"project" => project_params}) do
    project_params = Map.put(project_params, "user_id", conn.assigns.current_user_id)

    case Marketplace.create_project(project_params) do
      {:ok, project} ->
        conn |> put_status(:created) |> json(%{project: serialize_project(project)})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  def update(conn, %{"id" => id, "project" => project_params}) do
    case Marketplace.update_project(id, project_params) do
      {:ok, project} ->
        json(conn, %{project: serialize_project(project)})

      {:error, :not_found} ->
        conn |> put_status(:not_found) |> json(%{error: "Project not found"})

      {:error, changeset} ->
        conn |> put_status(:unprocessable_entity) |> json(%{errors: format_errors(changeset)})
    end
  end

  defp serialize_project(project) do
    %{
      id: project.id,
      name: project.name,
      description: project.description,
      status: project.status,
      start_date: project.start_date,
      end_date: project.end_date,
      budget: project.budget,
      spent: project.spent,
      job_id: project.job_id,
      contractor: serialize_user(project.contractor),
      homeowner: serialize_user(project.homeowner),
      created_at: project.inserted_at,
      updated_at: project.updated_at
    }
  end

  defp serialize_user(%{id: _} = user) do
    %{id: user.id, name: user.name, role: user.role}
  end

  defp serialize_user(_), do: nil

  defp format_errors(%Ecto.Changeset{} = changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end

  defp format_errors(reason), do: reason
end
