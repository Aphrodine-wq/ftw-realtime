defmodule FtwRealtime.Workers.EmailWorker do
  use Oban.Worker, queue: :default, max_attempts: 5

  alias FtwRealtime.{Mailer, Emails, Marketplace}

  @impl Oban.Worker
  def perform(%Oban.Job{args: %{"template" => template} = args}) do
    email = build_email(template, args)

    case email do
      nil -> :ok
      email -> Mailer.deliver(email)
    end
  end

  defp build_email("welcome", %{"user_id" => user_id}) do
    case Marketplace.get_user(user_id) do
      nil -> nil
      user -> Emails.welcome(user)
    end
  end

  defp build_email("bid_received", %{"homeowner_id" => hid, "job_id" => jid, "contractor_id" => cid}) do
    with homeowner when not is_nil(homeowner) <- Marketplace.get_user(hid),
         job when not is_nil(job) <- Marketplace.get_job(jid),
         contractor when not is_nil(contractor) <- Marketplace.get_user(cid) do
      Emails.bid_received(homeowner, job, contractor)
    else
      _ -> nil
    end
  end

  defp build_email("bid_accepted", %{"contractor_id" => cid, "job_id" => jid, "homeowner_id" => hid}) do
    with contractor when not is_nil(contractor) <- Marketplace.get_user(cid),
         job when not is_nil(job) <- Marketplace.get_job(jid),
         homeowner when not is_nil(homeowner) <- Marketplace.get_user(hid) do
      Emails.bid_accepted(contractor, job, homeowner)
    else
      _ -> nil
    end
  end

  defp build_email("verification_approved", %{"contractor_id" => cid, "step" => step}) do
    case Marketplace.get_user(cid) do
      nil -> nil
      contractor -> Emails.verification_approved(contractor, step)
    end
  end

  defp build_email("fair_record_generated", %{"contractor_id" => cid, "public_id" => public_id}) do
    case Marketplace.get_user(cid) do
      nil -> nil
      contractor -> Emails.fair_record_generated(contractor, %{public_id: public_id})
    end
  end

  defp build_email(_, _), do: nil
end
