package com.strata.ftw.service

import com.strata.ftw.domain.entity.Notification
import com.strata.ftw.domain.repository.NotificationRepository
import com.strata.ftw.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── New Bid on Job ──
    fun onBidPlaced(jobTitle: String, contractorName: String, homeownerId: UUID, jobId: UUID) {
        persist("bid_received", homeownerId, "New Bid Received",
            "$contractorName placed a bid on your job: $jobTitle",
            mapOf("job_id" to jobId.toString()))

        val homeowner = userRepository.findById(homeownerId).orElse(null)
        if (homeowner != null) {
            emailService.sendBidReceived(homeowner.email, jobTitle, contractorName)
        }
    }

    // ── Bid Accepted ──
    fun onBidAccepted(jobTitle: String, contractorId: UUID, jobId: UUID) {
        persist("bid_accepted", contractorId, "Bid Accepted",
            "Your bid on $jobTitle was accepted!",
            mapOf("job_id" to jobId.toString()))

        val contractor = userRepository.findById(contractorId).orElse(null)
        if (contractor != null) {
            emailService.sendBidAccepted(contractor.email, jobTitle)
        }
    }

    // ── Bid Rejected ──
    fun onBidRejected(jobTitle: String, contractorId: UUID, jobId: UUID) {
        persist("bid_rejected", contractorId, "Bid Not Selected",
            "Another contractor was selected for: $jobTitle",
            mapOf("job_id" to jobId.toString()))
    }

    // ── New Message ──
    @Async
    fun onMessageReceived(senderName: String, recipientId: UUID, conversationId: UUID) {
        persist("message_received", recipientId, "New Message",
            "$senderName sent you a message",
            mapOf("conversation_id" to conversationId.toString()))
    }

    // ── Job Status Change ──
    fun onJobStatusChange(jobTitle: String, newStatus: String, homeownerId: UUID, contractorId: UUID?, jobId: UUID) {
        persist("job_status_change", homeownerId, "Job Updated",
            "Your job \"$jobTitle\" is now: ${newStatus.replace("_", " ")}",
            mapOf("job_id" to jobId.toString(), "status" to newStatus))

        if (contractorId != null) {
            persist("job_status_change", contractorId, "Job Updated",
                "Job \"$jobTitle\" is now: ${newStatus.replace("_", " ")}",
                mapOf("job_id" to jobId.toString(), "status" to newStatus))
        }
    }

    // ── Invoice Created ──
    fun onInvoiceCreated(invoiceNumber: String, amount: Int, clientId: UUID?, contractorId: UUID) {
        // Notify contractor of successful creation
        persist("invoice_created", contractorId, "Invoice Created",
            "Invoice $invoiceNumber created for \$${amount / 100}.${"%02d".format(amount % 100)}",
            mapOf("invoice_number" to invoiceNumber))
    }

    // ── Payment Received ──
    fun onPaymentReceived(invoiceNumber: String, amount: Int, contractorId: UUID) {
        persist("payment_received", contractorId, "Payment Received",
            "Payment of \$${amount / 100}.${"%02d".format(amount % 100)} received for invoice $invoiceNumber",
            mapOf("invoice_number" to invoiceNumber))
    }

    // ── Review Received ──
    fun onReviewReceived(reviewerName: String, rating: Int, contractorId: UUID) {
        persist("review_received", contractorId, "New Review",
            "$reviewerName left a ${rating}-star review",
            mapOf("rating" to rating.toString()))
    }

    // ── Core persist + broadcast ──
    private fun persist(type: String, userId: UUID, title: String, body: String, metadata: Map<String, Any> = emptyMap()) {
        val notification = Notification(
            type = type,
            title = title,
            body = body,
            userId = userId,
            metadata = metadata
        )
        notificationRepository.save(notification)
        messagingTemplate.convertAndSend("/topic/user.$userId", mapOf(
            "event" to "notification",
            "data" to mapOf(
                "id" to notification.id.toString(),
                "type" to type,
                "title" to title,
                "body" to body,
                "read" to false,
                "metadata" to metadata,
                "created_at" to notification.insertedAt?.toString()
            )
        ))
    }
}
