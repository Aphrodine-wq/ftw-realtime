package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/reviews")
class ReviewController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(
        @RequestParam("for") forUserId: String?,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val userId = forUserId?.let { UUID.fromString(it) } ?: claims.userId
        val reviews = marketplace.listReviewsForUser(userId)
        return ResponseEntity.ok(mapOf("reviews" to reviews.map { marketplace.serializeReview(it) }))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val review = marketplace.getReview(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("review" to marketplace.serializeReview(review)))
    }

    @PostMapping
    fun create(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["review"] as? Map<String, Any> ?: body
        val review = marketplace.createReview(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("review" to marketplace.serializeReview(review)))
    }

    @PostMapping("/{id}/respond")
    fun respond(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<Any> {
        val review = marketplace.respondToReview(id, body["response"]!!)
        return ResponseEntity.ok(mapOf("review" to marketplace.serializeReview(review)))
    }
}
