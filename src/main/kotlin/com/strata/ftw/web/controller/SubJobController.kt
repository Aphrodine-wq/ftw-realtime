package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.*
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/sub-jobs")
class SubJobController(
    private val subJobRepository: SubJobRepository,
    private val subBidRepository: SubBidRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "open") status: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<Any> {
        val jobStatus = try { SubJobStatus.valueOf(status) } catch (_: Exception) { SubJobStatus.open }
        val subJobs = subJobRepository.findByStatus(jobStatus, PageRequest.of(0, limit))
        return ResponseEntity.ok(mapOf("sub_jobs" to subJobs.map { serializeSubJob(it) }))
    }

    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateSubJobRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val subJob = SubJob(
            contractorId = claims.userId,
            projectId = req.project_id ?: "",
            milestoneLabel = req.milestone_label ?: "",
            milestoneIndex = req.milestone_index,
            title = req.title,
            description = req.description,
            category = req.category,
            skills = req.skills,
            location = req.location,
            budgetMin = req.budget_min,
            budgetMax = req.budget_max,
            paymentPath = try {
                SubPaymentPath.valueOf(req.payment_path)
            } catch (_: Exception) { SubPaymentPath.contractor_escrow },
            disclosedToOwner = req.disclosed_to_owner,
            deadline = req.deadline?.let { Instant.parse(it) }
        )
        val saved = subJobRepository.save(subJob)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("sub_job" to serializeSubJob(saved)))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val subJob = subJobRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val bids = subBidRepository.findBySubJobIdOrderByInsertedAtAsc(id)
        return ResponseEntity.ok(mapOf(
            "sub_job" to serializeSubJob(subJob),
            "bids" to bids.map { serializeSubBid(it) }
        ))
    }

    @PostMapping("/{subJobId}/bids")
    fun placeBid(
        @PathVariable subJobId: UUID,
        @Valid @RequestBody req: PlaceSubBidRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val subJob = subJobRepository.findById(subJobId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        require(subJob.status == SubJobStatus.open) { "Sub job is not open for bids" }

        val existing = subBidRepository.findBySubJobIdAndSubContractorId(subJobId, claims.userId)
        require(existing == null) { "Already bid on this sub job" }

        val bid = SubBid(
            amount = req.amount,
            message = req.message,
            timeline = req.timeline
        )
        bid.subJob = subJob
        bid.subContractor = userRepository.findById(claims.userId).orElse(null)
        val saved = subBidRepository.save(bid)

        subJob.bidCount = subJob.bidCount + 1
        subJobRepository.save(subJob)

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("bid" to serializeSubBid(saved)))
    }

    @PostMapping("/{subJobId}/bids/{bidId}/accept")
    fun acceptBid(
        @PathVariable subJobId: UUID,
        @PathVariable bidId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val subJob = subJobRepository.findById(subJobId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (subJob.contractorId != claims.userId) {
            throw AccessDeniedException("Not your sub job")
        }

        val bid = subBidRepository.findById(bidId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        bid.status = BidStatus.accepted
        subBidRepository.save(bid)

        subJob.status = SubJobStatus.in_progress
        subJobRepository.save(subJob)

        return ResponseEntity.ok(mapOf("sub_job" to serializeSubJob(subJob), "bid" to serializeSubBid(bid)))
    }

    @GetMapping("/my-posts")
    fun myPosts(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val subJobs = subJobRepository.findByContractorIdOrderByInsertedAtDesc(claims.userId)
        return ResponseEntity.ok(mapOf("sub_jobs" to subJobs.map { serializeSubJob(it) }))
    }

    @GetMapping("/mine")
    fun mine(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> = myPosts(claims)

    @PutMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val subJob = subJobRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (subJob.contractorId != claims.userId) {
            throw AccessDeniedException("Not your sub job")
        }
        val newStatus = try {
            SubJobStatus.valueOf(body["status"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "status required")))
        } catch (_: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid status"))
        }
        subJob.status = newStatus
        val saved = subJobRepository.save(subJob)
        return ResponseEntity.ok(mapOf("sub_job" to serializeSubJob(saved)))
    }

    private fun serializeSubJob(sj: SubJob): Map<String, Any?> = mapOf(
        "id" to sj.id.toString(),
        "contractor_id" to sj.contractorId.toString(),
        "project_id" to sj.projectId,
        "milestone_label" to sj.milestoneLabel,
        "milestone_index" to sj.milestoneIndex,
        "title" to sj.title,
        "description" to sj.description,
        "category" to sj.category,
        "skills" to sj.skills,
        "location" to sj.location,
        "budget_min" to sj.budgetMin,
        "budget_max" to sj.budgetMax,
        "payment_path" to sj.paymentPath.name,
        "disclosed_to_owner" to sj.disclosedToOwner,
        "status" to sj.status.name,
        "deadline" to sj.deadline?.toString(),
        "bid_count" to sj.bidCount,
        "inserted_at" to sj.insertedAt?.toString(),
        "updated_at" to sj.updatedAt?.toString()
    )

    private fun serializeSubBid(sb: SubBid): Map<String, Any?> = mapOf(
        "id" to sb.id.toString(),
        "sub_job_id" to sb.subJobId.toString(),
        "sub_contractor_id" to sb.subContractorId.toString(),
        "amount" to sb.amount,
        "message" to sb.message,
        "timeline" to sb.timeline,
        "status" to sb.status.name,
        "inserted_at" to sb.insertedAt?.toString(),
        "updated_at" to sb.updatedAt?.toString()
    )
}
