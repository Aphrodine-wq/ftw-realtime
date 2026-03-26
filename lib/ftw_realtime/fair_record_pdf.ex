defmodule FtwRealtime.FairRecordPdf do
  @moduledoc """
  Generates a printable HTML certificate for FairRecord verification.
  Returns HTML that can be opened in a browser and printed to PDF.
  """

  @doc """
  Generate a printable HTML certificate for the given fair record.
  Returns `{:ok, html_string}`.
  """
  def generate_html(record) do
    contractor = record.contractor || %{name: "Unknown", rating: 0.0, jobs_completed: 0}
    contractor_name = Map.get(contractor, :name, "Unknown")
    contractor_rating = Map.get(contractor, :rating, 0.0) || 0.0
    contractor_jobs = Map.get(contractor, :jobs_completed, 0) || 0

    budget_color = if record.on_budget, do: "#059669", else: "#D97706"
    timeline_color = if record.on_time, do: "#059669", else: "#D97706"
    dispute_color = if record.dispute_count == 0, do: "#059669", else: "#D97706"

    timeline_text = if record.on_time, do: "On Time", else: "Late"
    dispute_text =
      if record.dispute_count == 0,
        do: "Clean record",
        else: "#{record.dispute_count} dispute(s) filed"

    completion_date = format_date(record.actual_completion_date)
    confirmed_date = format_date(record.confirmed_at)

    footer_left_html =
      if record.homeowner_confirmed do
        ~s(<span class="verified-badge">Verified Completion</span><span>Confirmed #{confirmed_date}</span>)
      else
        ~s(<span>Pending homeowner confirmation</span>)
      end

    html = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <title>FairRecord Certificate - #{record.public_id}</title>
      <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          background: #FDFBF8;
          color: #0F1419;
          padding: 40px;
        }
        .certificate {
          max-width: 800px;
          margin: 0 auto;
          background: #fff;
          border-radius: 12px;
          overflow: hidden;
          box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .header {
          padding: 32px 40px 24px;
          border-bottom: 1px solid #E5E1DB;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .brand {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .brand-icon {
          width: 36px;
          height: 36px;
          background: #C41E3A;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          font-weight: 700;
          font-size: 14px;
        }
        .brand-text { font-size: 18px; font-weight: 700; }
        .subtitle { font-size: 12px; color: #9CA3AF; margin-top: 2px; }
        .public-id {
          font-family: monospace;
          font-size: 13px;
          color: #9CA3AF;
          background: #F7F8FA;
          padding: 6px 12px;
          border-radius: 8px;
        }
        .section {
          padding: 24px 40px;
          border-bottom: 1px solid #E5E1DB;
        }
        .section-title {
          font-size: 11px;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: #0F1419;
          margin-bottom: 16px;
        }
        .project-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
        .info-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 12px;
        }
        .info-item label {
          display: block;
          font-size: 11px;
          color: #9CA3AF;
          margin-bottom: 2px;
        }
        .info-item span {
          font-size: 14px;
          font-weight: 500;
        }
        .metrics {
          display: grid;
          grid-template-columns: 1fr 1fr 1fr;
          gap: 16px;
        }
        .metric {
          text-align: center;
          padding: 16px;
          background: #F7F8FA;
          border-radius: 12px;
        }
        .metric-label {
          font-size: 11px;
          color: #9CA3AF;
          margin-bottom: 6px;
        }
        .metric-value {
          font-size: 28px;
          font-weight: 700;
          line-height: 1;
        }
        .metric-detail {
          font-size: 12px;
          color: #6B7280;
          margin-top: 6px;
        }
        .scope-text {
          font-size: 14px;
          color: #4B5563;
          line-height: 1.6;
        }
        .contractor-row {
          display: flex;
          align-items: center;
          gap: 16px;
        }
        .contractor-avatar {
          width: 48px;
          height: 48px;
          border-radius: 50%;
          background: #FDF2F3;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #C41E3A;
          font-weight: 700;
          font-size: 14px;
          flex-shrink: 0;
        }
        .contractor-name { font-size: 15px; font-weight: 700; }
        .contractor-detail { font-size: 13px; color: #6B7280; margin-top: 2px; }
        .footer {
          padding: 20px 40px;
          background: #F7F8FA;
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        .footer-left {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: #6B7280;
        }
        .footer-right {
          font-family: monospace;
          font-size: 11px;
          color: #9CA3AF;
        }
        .verified-badge {
          display: inline-block;
          background: #ECFDF5;
          color: #059669;
          font-size: 11px;
          font-weight: 600;
          padding: 3px 8px;
          border-radius: 6px;
        }
        @media print {
          body { padding: 0; background: #fff; }
          .certificate { box-shadow: none; border-radius: 0; }
        }
      </style>
    </head>
    <body>
      <div class="certificate">
        <div class="header">
          <div>
            <div class="brand">
              <div class="brand-icon">FT</div>
              <div class="brand-text">FairTradeWorker</div>
            </div>
            <div class="subtitle">Verified Project Completion Certificate</div>
          </div>
          <div class="public-id">#{record.public_id}</div>
        </div>

        <div class="section">
          <div class="project-title">#{escape_html(record.category || "Project")}</div>
          <div class="info-grid">
            <div class="info-item">
              <label>Category</label>
              <span>#{escape_html(record.category || "N/A")}</span>
            </div>
            <div class="info-item">
              <label>Location</label>
              <span>#{escape_html(record.location_city || "N/A")}, TX</span>
            </div>
            <div class="info-item">
              <label>Completed</label>
              <span>#{completion_date}</span>
            </div>
            <div class="info-item">
              <label>Rating</label>
              <span>#{record.avg_rating || 0.0} / 5.0 (#{record.review_count || 0} reviews)</span>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Performance Metrics</div>
          <div class="metrics">
            <div class="metric">
              <div class="metric-label">Budget Accuracy</div>
              <div class="metric-value" style="color: #{budget_color}">#{record.budget_accuracy_pct || 0}%</div>
              <div class="metric-detail">$#{format_cents(record.final_cost)} of $#{format_cents(record.estimated_budget)}</div>
            </div>
            <div class="metric">
              <div class="metric-label">Timeline</div>
              <div class="metric-value" style="color: #{timeline_color}">#{timeline_text}</div>
              <div class="metric-detail">#{if record.on_time, do: "Completed on schedule", else: "Completed #{completion_date}"}</div>
            </div>
            <div class="metric">
              <div class="metric-label">Disputes</div>
              <div class="metric-value" style="color: #{dispute_color}">#{record.dispute_count || 0}</div>
              <div class="metric-detail">#{dispute_text}</div>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Scope of Work</div>
          <div class="scope-text">#{escape_html(record.scope_summary || "No scope summary available.")}</div>
        </div>

        <div class="section">
          <div class="section-title">Contractor</div>
          <div class="contractor-row">
            <div class="contractor-avatar">#{initials(contractor_name)}</div>
            <div>
              <div class="contractor-name">#{escape_html(contractor_name)}</div>
              <div class="contractor-detail">#{contractor_rating} rating -- #{contractor_jobs} jobs completed</div>
            </div>
          </div>
        </div>

        <div class="footer">
          <div class="footer-left">
            #{footer_left_html}
          </div>
          <div class="footer-right">#{record.public_id}</div>
        </div>
      </div>
    </body>
    </html>
    """

    {:ok, html}
  end

  # --- Helpers ---

  defp escape_html(nil), do: ""

  defp escape_html(text) when is_binary(text) do
    text
    |> String.replace("&", "&amp;")
    |> String.replace("<", "&lt;")
    |> String.replace(">", "&gt;")
    |> String.replace("\"", "&quot;")
  end

  defp escape_html(other), do: escape_html(to_string(other))

  defp format_date(nil), do: "N/A"
  defp format_date(%Date{} = d), do: Calendar.strftime(d, "%B %d, %Y")
  defp format_date(%DateTime{} = dt), do: Calendar.strftime(dt, "%B %d, %Y")
  defp format_date(%NaiveDateTime{} = ndt), do: Calendar.strftime(ndt, "%B %d, %Y")
  defp format_date(other) when is_binary(other), do: other
  defp format_date(_), do: "N/A"

  defp format_cents(nil), do: "0"
  defp format_cents(cents) when is_integer(cents) do
    dollars = div(cents, 100)
    Integer.to_string(dollars) |> add_commas()
  end
  defp format_cents(amount), do: to_string(amount)

  defp add_commas(str) do
    str
    |> String.reverse()
    |> String.replace(~r/(\d{3})(?=\d)/, "\\1,")
    |> String.reverse()
  end

  defp initials(name) when is_binary(name) do
    name
    |> String.split(" ")
    |> Enum.take(2)
    |> Enum.map(&String.first/1)
    |> Enum.join("")
    |> String.upcase()
  end

  defp initials(_), do: "?"
end
