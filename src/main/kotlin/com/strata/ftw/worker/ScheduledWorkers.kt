package com.strata.ftw.worker

import com.strata.ftw.ai.AiGateway
import com.strata.ftw.domain.entity.RevenueSnapshot
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.FairTrustService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ScheduledWorkers(
    private val aiGateway: AiGateway,
    private val fairTrust: FairTrustService,
    private val payoutService: com.strata.ftw.service.PayoutService,
    private val userRepository: UserRepository,
    private val revenueSnapshotRepository: RevenueSnapshotRepository,
    private val jobRepository: JobRepository,
    private val bidRepository: BidRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Weekly Sunday 3am UTC — refresh FairPrice cache from DB
    @Scheduled(cron = "0 0 3 * * SUN")
    fun refreshFairPrices() {
        log.info("Refreshing FairPrice cache from database")
        aiGateway.refreshFairPrices()
        log.info("FairPrice cache refreshed")
    }

    // Daily 4am UTC — cleanup expired FairScope cache entries
    @Scheduled(cron = "0 0 4 * * *")
    fun cleanupFairScope() {
        log.info("Cleaning up expired FairScope cache entries")
        val remaining = aiGateway.cleanupFairScope()
        log.info("FairScope cleanup done, {} entries remaining", remaining)
    }

    // Daily 5am UTC — mark expired verifications
    @Scheduled(cron = "0 0 5 * * *")
    fun checkVerificationExpiry() {
        log.info("Checking for expired verifications")
        val count = fairTrust.checkExpirations()
        log.info("Marked {} verifications as expired", count)
    }

    // Weekly Sunday 6am UTC — recompute quality scores
    @Scheduled(cron = "0 0 6 * * SUN")
    fun recomputeQualityScores() {
        log.info("Recomputing contractor quality scores")
        val contractors = userRepository.findAll().filter { it.role.name == "contractor" }
        contractors.forEach { contractor ->
            try {
                fairTrust.computeQualityScore(contractor.id!!)
            } catch (e: Exception) {
                log.error("Failed to compute quality score for contractor {}", contractor.id, e)
            }
        }
        log.info("Quality score recomputation complete for {} contractors", contractors.size)
    }

    // Every 15 minutes — retry failed payouts
    @Scheduled(cron = "0 */15 * * * *")
    fun retryFailedPayouts() {
        log.info("Retrying failed payouts")
        val retried = payoutService.retryFailedPayouts()
        if (retried > 0) {
            log.info("Retried {} payouts", retried)
        }
    }

    // Daily midnight UTC — revenue snapshot
    @Scheduled(cron = "0 0 0 * * *")
    fun captureRevenueSnapshot() {
        val yesterday = LocalDate.now().minusDays(1)
        if (revenueSnapshotRepository.findByDate(yesterday) != null) return

        log.info("Capturing revenue snapshot for {}", yesterday)
        val snapshot = RevenueSnapshot(date = yesterday)
        revenueSnapshotRepository.save(snapshot)
        log.info("Revenue snapshot saved for {}", yesterday)
    }
}
