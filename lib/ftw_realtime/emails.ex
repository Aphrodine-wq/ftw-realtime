defmodule FtwRealtime.Emails do
  @moduledoc "Transactional email templates for FairTradeWorker."

  import Swoosh.Email

  @from {"FairTradeWorker", "noreply@fairtradeworker.com"}

  def welcome(user) do
    new()
    |> to({user.name, user.email})
    |> from(@from)
    |> subject("Welcome to FairTradeWorker")
    |> text_body("""
    Hey #{user.name},

    Welcome to FairTradeWorker — the fair way to find and hire contractors.

    #{role_welcome(user.role)}

    If you have questions, just reply to this email.

    — The FairTradeWorker Team
    """)
  end

  def bid_received(homeowner, job, contractor) do
    new()
    |> to({homeowner.name, homeowner.email})
    |> from(@from)
    |> subject("New bid on your #{job.title} project")
    |> text_body("""
    #{homeowner.name},

    #{contractor.name} just submitted a bid on your project "#{job.title}".

    Log in to review the bid and compare with others:
    https://fairtradeworker.com/homeowner/bids

    — FairTradeWorker
    """)
  end

  def bid_accepted(contractor, job, homeowner) do
    new()
    |> to({contractor.name, contractor.email})
    |> from(@from)
    |> subject("Your bid was accepted — #{job.title}")
    |> text_body("""
    #{contractor.name},

    #{homeowner.name} accepted your bid on "#{job.title}".

    Log in to get started on the project:
    https://fairtradeworker.com/contractor/projects

    — FairTradeWorker
    """)
  end

  def invoice_sent(homeowner, invoice_amount, contractor_name) do
    new()
    |> to({homeowner.name, homeowner.email})
    |> from(@from)
    |> subject("Invoice from #{contractor_name}")
    |> text_body("""
    #{homeowner.name},

    #{contractor_name} sent you an invoice for $#{format_amount(invoice_amount)}.

    You can pay securely through QuickBooks:
    https://fairtradeworker.com/homeowner/projects

    — FairTradeWorker
    """)
  end

  def verification_approved(contractor, step) do
    new()
    |> to({contractor.name, contractor.email})
    |> from(@from)
    |> subject("Verification approved: #{step}")
    |> text_body("""
    #{contractor.name},

    Your #{step} verification has been approved. You're one step closer to being fully verified on FairTradeWorker.

    Check your verification status:
    https://fairtradeworker.com/contractor/settings

    — FairTradeWorker
    """)
  end

  def fair_record_generated(contractor, record) do
    new()
    |> to({contractor.name, contractor.email})
    |> from(@from)
    |> subject("FairRecord generated — #{record.public_id}")
    |> text_body("""
    #{contractor.name},

    A new FairRecord has been generated for your completed project.

    Share it with future clients:
    https://fairtradeworker.com/record/#{record.public_id}

    View all your records:
    https://fairtradeworker.com/contractor/records

    — FairTradeWorker
    """)
  end

  defp role_welcome(:contractor) do
    "As a contractor, you can browse jobs, submit bids, and manage your projects — all in one place. No lead fees, ever."
  end

  defp role_welcome(:homeowner) do
    "Post your first job and verified contractors will start bidding. You'll see their profiles, ratings, and FairPrice comparisons."
  end

  defp role_welcome(_), do: ""

  defp format_amount(cents) when is_integer(cents), do: :erlang.float_to_binary(cents / 100, decimals: 2)
  defp format_amount(amount), do: to_string(amount)
end
