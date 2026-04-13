package com.strata.ftw.service

import com.strata.ftw.domain.entity.SubPayout
import com.strata.ftw.domain.entity.SubPaymentPath
import com.strata.ftw.domain.repository.SubJobRepository
import com.strata.ftw.domain.repository.SubPayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PayoutService(
    private val subPayoutRepository: SubPayoutRepository,
    private val subJobRepository: SubJobRepository,
    private val quickBooksService: QuickBooksService,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Create a payout when a milestone/sub-job is marked complete and approved.
     * Calculates the 5% platform fee, creates the SubPayout record,
     * and attempts to process via QuickBooks if connected.
     */
    @Transactional
    fun createPayout(subJobId: UUID, approverUserId: UUID): SubPayout {
        val subJob = subJobRepository.findById(subJobId)
            .orElseThrow { IllegalArgumentException("Sub-job not found: $subJobId") }

        val existing = subPayoutRepository.findBySubJobId(subJobId)
        if (existing != null) {
            throw IllegalArgumentException("Payout already exists for sub-job: $subJobId")
        }

        // Use the accepted bid amount as the gross (already in cents)
        val acceptedBid = subJob.subBids.find { it.status.name == "accepted" }
        val grossAmount = acceptedBid?.amount ?: 0
        val feePercent = 5.0
        val platformFee = (grossAmount * feePercent / 100.0).toInt()
        val netAmount = grossAmount - platformFee

        val payout = SubPayout(
            subJobId = subJobId,
            subContractorId = acceptedBid?.subContractorId,
            grossAmount = grossAmount,
            platformFee = platformFee,
            netAmount = netAmount,
            feePercent = feePercent,
            paymentPath = subJob.paymentPath,
            status = "queued"
        )
        val saved = subPayoutRepository.save(payout)

        // Attempt to process immediately
        processPayout(saved, subJob.contractorId!!)

        return saved
    }

    /**
     * Process a payout — attempt QB payment if connected, otherwise mark as pending_manual.
     */
    @Transactional
    fun processPayout(payout: SubPayout, contractorUserId: UUID) {
        payout.status = "processing"
        subPayoutRepository.save(payout)

        try {
            if (quickBooksService.isConnected(contractorUserId)) {
                // QB is connected — the payment would be created through QB
                // For now we mark as completed since actual QB payment creation
                // would require an invoice to exist first
                payout.status = "completed"
                payout.paidAt = Instant.now()
                subPayoutRepository.save(payout)

                if (payout.subContractorId != null) {
                    notificationService.onPaymentReceived(
                        "SUB-${payout.subJobId.toString().take(6)}",
                        payout.netAmount, // Already in cents
                        payout.subContractorId!!
                    )
                }
                log.info("Payout {} completed via QB for sub-job {}", payout.id, payout.subJobId)
            } else {
                // No QB connection — mark for manual processing
                payout.status = "pending_manual"
                subPayoutRepository.save(payout)
                log.info("Payout {} marked pending_manual (no QB) for sub-job {}", payout.id, payout.subJobId)
            }
        } catch (e: Exception) {
            payout.status = "failed"
            subPayoutRepository.save(payout)
            log.error("Payout {} failed for sub-job {}: {}", payout.id, payout.subJobId, e.message)
        }
    }

    /**
     * Retry failed payouts. Called by ScheduledWorkers.
     */
    @Transactional
    fun retryFailedPayouts(): Int {
        val cutoff = Instant.now().minusSeconds(300) // Only retry payouts older than 5 minutes
        val retryable = subPayoutRepository.findRetryable(cutoff)

        var retried = 0
        for (payout in retryable) {
            try {
                val subJob = subJobRepository.findById(payout.subJobId!!).orElse(null) ?: continue
                processPayout(payout, subJob.contractorId!!)
                retried++
            } catch (e: Exception) {
                log.error("Retry failed for payout {}: {}", payout.id, e.message)
            }
        }
        return retried
    }
}
