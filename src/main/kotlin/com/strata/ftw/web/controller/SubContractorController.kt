package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.SubJobStatus
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/sub-contractors")
class SubContractorController(
    private val subJobRepository: SubJobRepository,
    private val subBidRepository: SubBidRepository,
    private val subPayoutRepository: SubPayoutRepository,
    private val reviewRepository: ReviewRepository
) {

    @GetMapping("/stats")
    fun stats(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val uid = claims.userId
        val now = Instant.now()
        val monthStart = now.minus(30, ChronoUnit.DAYS)
        val prevMonthStart = monthStart.minus(30, ChronoUnit.DAYS)

        val activeSubJobs = subJobRepository.countByContractorIdAndStatus(uid, SubJobStatus.in_progress)
        val completedSubJobs = subJobRepository.countByContractorIdAndStatus(uid, SubJobStatus.completed)

        val monthlyRevenue = subPayoutRepository.sumNetAmountSince(uid, monthStart)
        val prevMonthRevenue = subPayoutRepository.sumNetAmountBetween(uid, prevMonthStart, monthStart)
        val revenueChange = if (prevMonthRevenue > 0) ((monthlyRevenue - prevMonthRevenue) / prevMonthRevenue) * 100 else 0.0

        val avgRating = reviewRepository.averageRatingForUser(uid) ?: 0.0

        val totalBids = subBidRepository.findBySubContractorId(uid).size.toLong()
        val acceptedBids = subBidRepository.countAcceptedBySubContractorId(uid)
        val winRate = if (totalBids > 0) acceptedBids.toDouble() / totalBids else 0.0

        val pendingBids = subBidRepository.countPendingBySubContractorId(uid)

        return ResponseEntity.ok(mapOf(
            "activeSubJobs" to activeSubJobs,
            "completedSubJobs" to completedSubJobs,
            "monthlyRevenue" to monthlyRevenue.toInt(),
            "revenueChange" to revenueChange,
            "avgRating" to avgRating,
            "winRate" to winRate,
            "responseTime" to "< 2 hrs",
            "pendingBids" to pendingBids
        ))
    }
}
