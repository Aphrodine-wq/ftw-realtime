package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.JobStatus
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
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
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["job"] as? Map<String, Any> ?: body
        val job = marketplace.postJob(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("job" to marketplace.serializeJob(job)))
    }

    @PostMapping("/{jobId}/bids")
    fun placeBid(
        @PathVariable jobId: UUID,
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val attrs = body["bid"] as? Map<String, Any> ?: body
            val bid = marketplace.placeBid(jobId, attrs, claims.userId)
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf("bid" to marketplace.serializeBid(bid)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{jobId}/bids/{bidId}/accept")
    fun acceptBid(
        @PathVariable jobId: UUID,
        @PathVariable bidId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val bid = marketplace.acceptBid(jobId, bidId, claims.userId)
            ResponseEntity.ok(mapOf("bid" to marketplace.serializeBid(bid)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{jobId}/transition")
    fun transitionJob(
        @PathVariable jobId: UUID,
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val newStatus = JobStatus.valueOf(body["status"]!!)
            val job = marketplace.transitionJob(jobId, newStatus, claims.userId)
            ResponseEntity.ok(mapOf("job" to marketplace.serializeJob(job)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
