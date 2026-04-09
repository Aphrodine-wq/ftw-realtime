package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.JobStatus
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/jobs")
class JobController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun listJobs(
        @RequestParam status: String?,
        @RequestParam category: String?,
        @RequestParam limit: Int?
    ): ResponseEntity<Any> {
        val jobStatus = status?.let { JobStatus.valueOf(it) }
        val jobs = marketplace.listJobs(jobStatus, category, limit ?: 20)
        return ResponseEntity.ok(mapOf("jobs" to jobs.map { marketplace.serializeJob(it) }))
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: UUID): ResponseEntity<Any> {
        val job = marketplace.getJob(id) ?: return ResponseEntity.notFound().build()
        val bids = marketplace.listBids(id)
        return ResponseEntity.ok(mapOf(
            "job" to marketplace.serializeJob(job),
            "bids" to bids.map { marketplace.serializeBid(it) }
        ))
    }

    @PostMapping
    fun createJob(
        @Valid @RequestBody req: CreateJobRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mapOf<String, Any?>(
            "title" to req.title,
            "description" to req.description,
            "category" to req.category,
            "budget_min" to req.budget_min,
            "budget_max" to req.budget_max,
            "location" to req.location
        ).filterValues { it != null }.mapValues { it.value!! }
        val job = marketplace.postJob(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("job" to marketplace.serializeJob(job)))
    }

    @PostMapping("/{jobId}/bids")
    fun placeBid(
        @PathVariable jobId: UUID,
        @Valid @RequestBody req: PlaceBidRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mapOf<String, Any?>(
            "amount" to req.amount,
            "message" to req.message,
            "timeline" to req.timeline
        ).filterValues { it != null }.mapValues { it.value!! }
        val bid = marketplace.placeBid(jobId, attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("bid" to marketplace.serializeBid(bid)))
    }

    @PostMapping("/{jobId}/bids/{bidId}/accept")
    fun acceptBid(
        @PathVariable jobId: UUID,
        @PathVariable bidId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val bid = marketplace.acceptBid(jobId, bidId, claims.userId)
        return ResponseEntity.ok(mapOf("bid" to marketplace.serializeBid(bid)))
    }

    @PostMapping("/{jobId}/transition")
    fun transitionJob(
        @PathVariable jobId: UUID,
        @Valid @RequestBody req: TransitionJobRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val newStatus = JobStatus.valueOf(req.status)
        val job = marketplace.transitionJob(jobId, newStatus, claims.userId)
        return ResponseEntity.ok(mapOf("job" to marketplace.serializeJob(job)))
    }
}
