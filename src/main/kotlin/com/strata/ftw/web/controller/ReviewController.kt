package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.JobStatus
import com.strata.ftw.domain.repository.BidRepository
import com.strata.ftw.domain.repository.JobRepository
import com.strata.ftw.domain.repository.ReviewRepository
import com.strata.ftw.domain.repository.UserRepository
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.NotificationService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/reviews")
class ReviewController(
    private val marketplace: MarketplaceService,
    private val reviewRepository: ReviewRepository,
    private val jobRepository: JobRepository,
    private val bidRepository: BidRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {

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

    /** GET /api/reviews/contractor/{id} — all reviews for a contractor */
    @GetMapping("/contractor/{contractorId}")
    fun contractorReviews(@PathVariable contractorId: UUID): ResponseEntity<Any> {
        val reviews = marketplace.listReviewsForUser(contractorId)
        return ResponseEntity.ok(mapOf("reviews" to reviews.map { marketplace.serializeReview(it) }))
    }

    /** GET /api/reviews/stats/{id} — avg rating, count, breakdown */
    @GetMapping("/stats/{contractorId}")
    fun stats(@PathVariable contractorId: UUID): ResponseEntity<Any> {
        val avgRating = reviewRepository.averageRatingForUser(contractorId) ?: 0.0
        val count = reviewRepository.countByReviewedId(contractorId)
        val breakdown = reviewRepository.ratingBreakdown(contractorId).associate {
            (it[0] as Int).toString() to (it[1] as Long)
        }
        return ResponseEntity.ok(mapOf(
            "avg_rating" to avgRating,
            "count" to count,
            "breakdown" to breakdown
        ))
    }

    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateReviewRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        // Validation: only homeowners who had a completed job with this contractor can review
        if (req.job_id != null) {
            val job = jobRepository.findById(req.job_id).orElse(null)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Job not found"))
            require(job.homeownerId == claims.userId) { "You can only review contractors from your own jobs" }
            require(job.status == JobStatus.completed) { "Job must be completed before leaving a review" }

            // Check the reviewed contractor had an accepted bid on this job
            val acceptedBid = bidRepository.findByJobIdAndContractorId(req.job_id, req.reviewed_id)
            require(acceptedBid != null) { "This contractor did not work on this job" }

            // Check for duplicate review
            val existing = reviewRepository.findByReviewerIdAndJobId(claims.userId, req.job_id)
            require(existing == null) { "You already reviewed this job" }
        }

        val attrs = mapOf<String, Any>(
            "rating" to req.rating,
            "reviewed_id" to req.reviewed_id.toString(),
            "comment" to (req.comment ?: ""),
            "job_id" to (req.job_id?.toString() ?: "")
        ).filterValues { it.toString().isNotBlank() }

        val review = marketplace.createReview(attrs, claims.userId)

        // Update contractor's cached rating
        val newAvg = reviewRepository.averageRatingForUser(req.reviewed_id) ?: 0.0
        val contractor = userRepository.findById(req.reviewed_id).orElse(null)
        if (contractor != null) {
            contractor.rating = newAvg
            userRepository.save(contractor)
        }

        // Notify contractor
        val reviewer = userRepository.findById(claims.userId).orElse(null)
        notificationService.onReviewReceived(reviewer?.name ?: "A homeowner", req.rating, req.reviewed_id)

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("review" to marketplace.serializeReview(review)))
    }

    @PostMapping("/{id}/respond")
    fun respond(
        @PathVariable id: UUID,
        @Valid @RequestBody req: RespondToReviewRequest
    ): ResponseEntity<Any> {
        val review = marketplace.respondToReview(id, req.response)
        return ResponseEntity.ok(mapOf("review" to marketplace.serializeReview(review)))
    }
}
