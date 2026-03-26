package com.strata.ftw.service

import com.strata.ftw.domain.entity.Verification
import com.strata.ftw.domain.repository.ReviewRepository
import com.strata.ftw.domain.repository.FairRecordRepository
import com.strata.ftw.domain.repository.UserRepository
import com.strata.ftw.domain.repository.VerificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FairTrustService(
    private val verificationRepository: VerificationRepository,
    private val reviewRepository: ReviewRepository,
    private val fairRecordRepository: FairRecordRepository,
    private val userRepository: UserRepository
) {
    private val steps = listOf("license", "insurance", "background", "identity")

    fun verificationStatus(contractorId: UUID): Map<String, Any> {
        val verifications = verificationRepository.findByContractorId(contractorId)
        val stepMap = steps.associateWith { step ->
            verifications.find { it.step == step }?.let {
                mapOf("status" to it.status, "reviewed_at" to it.reviewedAt?.toString(), "expires_at" to it.expiresAt?.toString())
            } ?: mapOf("status" to "not_started")
        }
        val approvedCount = verifications.count { it.status == "approved" }
        return mapOf(
            "steps" to stepMap,
            "fully_verified" to (approvedCount == steps.size),
            "pending_count" to verifications.count { it.status == "pending" },
            "approved_count" to approvedCount,
            "total_steps" to steps.size
        )
    }

    @Transactional
    fun submitVerification(contractorId: UUID, step: String, data: Map<String, Any>): Map<String, Any> {
        require(step in steps) { "Invalid step: $step" }
        val existing = verificationRepository.findByContractorIdAndStep(contractorId, step)
        val verification = existing ?: Verification(contractorId = contractorId, step = step)
        verification.status = "pending"
        verification.data = data
        val saved = verificationRepository.save(verification)
        return mapOf("id" to saved.id.toString(), "step" to saved.step, "status" to saved.status)
    }

    @Transactional
    fun approveVerification(verificationId: UUID, adminId: UUID, expiresAt: Instant? = null) {
        val v = verificationRepository.findById(verificationId).orElseThrow()
        v.status = "approved"
        v.reviewedBy = adminId
        v.reviewedAt = Instant.now()
        v.expiresAt = expiresAt
        verificationRepository.save(v)
    }

    @Transactional
    fun rejectVerification(verificationId: UUID, adminId: UUID, notes: String?) {
        val v = verificationRepository.findById(verificationId).orElseThrow()
        v.status = "rejected"
        v.reviewedBy = adminId
        v.reviewedAt = Instant.now()
        v.notes = notes
        verificationRepository.save(v)
    }

    @Transactional
    fun checkExpirations(): Int {
        val expired = verificationRepository.findExpired(Instant.now())
        expired.forEach { it.status = "expired"; verificationRepository.save(it) }
        return expired.size
    }

    fun computeQualityScore(contractorId: UUID): Int {
        val avgRating = reviewRepository.averageRatingForUser(contractorId) ?: 0.0
        val reviewCount = reviewRepository.countByReviewedId(contractorId)

        val totalRecords = fairRecordRepository.countConfirmed(contractorId)
        val onTimeCount = fairRecordRepository.countOnTime(contractorId)
        val avgBudgetAccuracy = fairRecordRepository.avgBudgetAccuracy(contractorId) ?: 0.0

        val ratingScore = (avgRating / 5.0) * 40
        val completionRate = if (reviewCount > 0) minOf(1.0, reviewCount.toDouble() / 10) * 25 else 0.0
        val onTimeRate = if (totalRecords > 0) (onTimeCount.toDouble() / totalRecords) * 20 else 0.0
        val budgetScore = (avgBudgetAccuracy / 100.0) * 15

        val score = (ratingScore + completionRate + onTimeRate + budgetScore).toInt().coerceIn(0, 100)

        val user = userRepository.findById(contractorId).orElse(null)
        if (user != null) {
            user.qualityScore = score
            userRepository.save(user)
        }

        return score
    }

    fun handlePersonaWebhook(body: Map<String, Any>, signature: String?) {
        // TODO: Verify signature and process Persona KYC webhook
    }

    fun handleCheckrWebhook(body: Map<String, Any>, signature: String?) {
        // TODO: Verify signature and process Checkr background check webhook
    }
}
