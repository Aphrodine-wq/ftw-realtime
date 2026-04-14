package com.strata.ftw.web.controller

import com.strata.ftw.domain.repository.PayoutRepository
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/payouts")
class PayoutController(private val payoutRepository: PayoutRepository) {

    @GetMapping
    fun list(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val payouts = payoutRepository.findByContractorId(claims.userId)
        return ResponseEntity.ok(mapOf("payouts" to payouts.map { payout ->
            mapOf(
                "id" to payout.id,
                "bid_id" to payout.bidId,
                "gross_amount" to payout.grossAmount,
                "platform_fee" to payout.platformFee,
                "net_amount" to payout.netAmount,
                "fee_percent" to payout.feePercent,
                "status" to payout.status.name,
                "failure_reason" to payout.failureReason,
                "paid_at" to payout.paidAt,
                "inserted_at" to payout.insertedAt
            )
        }))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val payout = payoutRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Verify ownership through the bid's contractor
        val bid = payout.bid
        if (bid == null || bid.contractorId != claims.userId) {
            return ResponseEntity.status(403).body(mapOf("error" to "Not authorized"))
        }

        return ResponseEntity.ok(mapOf("payout" to mapOf(
            "id" to payout.id,
            "bid_id" to payout.bidId,
            "gross_amount" to payout.grossAmount,
            "platform_fee" to payout.platformFee,
            "net_amount" to payout.netAmount,
            "fee_percent" to payout.feePercent,
            "status" to payout.status.name,
            "failure_reason" to payout.failureReason,
            "qb_bill_id" to payout.qbBillId,
            "qb_bill_payment_id" to payout.qbBillPaymentId,
            "paid_at" to payout.paidAt,
            "inserted_at" to payout.insertedAt
        )))
    }
}
