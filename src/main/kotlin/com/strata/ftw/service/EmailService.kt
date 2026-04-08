package com.strata.ftw.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.host:}") private val smtpHost: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val from = "noreply@fairtradeworker.com"

    @Async
    fun sendWelcome(email: String, name: String) {
        send(email, "Welcome to FairTradeWorker", """
            Hi $name,

            Welcome to FairTradeWorker! We're glad you're here.

            If you're a contractor, complete your verification to start receiving jobs.
            If you're a homeowner, post your first job and watch the bids come in.

            — The FairTradeWorker Team
        """.trimIndent())
    }

    @Async
    fun sendBidReceived(email: String, jobTitle: String, contractorName: String) {
        send(email, "New Bid on $jobTitle", """
            You received a new bid from $contractorName on your job: $jobTitle.

            Log in to review the bid and accept when ready.
        """.trimIndent())
    }

    @Async
    fun sendPasswordReset(email: String, name: String, resetUrl: String) {
        send(email, "Reset Your Password", """
            Hi $name,

            We received a request to reset your password. Click the link below to set a new one:

            $resetUrl

            This link expires in 15 minutes. If you didn't request this, ignore this email.

            — The FairTradeWorker Team
        """.trimIndent())
    }

    @Async
    fun sendBidAccepted(email: String, jobTitle: String) {
        send(email, "Your Bid Was Accepted", """
            Your bid on $jobTitle was accepted.

            Log in to get started on the project.
        """.trimIndent())
    }

    private fun send(to: String, subject: String, body: String) {
        if (smtpHost.isBlank()) {
            log.info("SMTP not configured, skipping email to={} subject={}", to, subject)
            return
        }
        try {
            val msg = SimpleMailMessage()
            msg.from = from
            msg.setTo(to)
            msg.subject = subject
            msg.text = body
            mailSender.send(msg)
            log.info("Email sent to={} subject={}", to, subject)
        } catch (e: Exception) {
            log.error("Failed to send email to={} subject={}", to, subject, e)
        }
    }
}
