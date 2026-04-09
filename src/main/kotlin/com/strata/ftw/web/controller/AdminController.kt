package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.UserRole
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val userRepository: UserRepository,
    private val jobRepository: JobRepository,
    private val disputeRepository: DisputeRepository,
    private val reviewRepository: ReviewRepository,
    private val invoiceRepository: InvoiceRepository,
    private val bidRepository: BidRepository
) {

    private fun requireAdmin(claims: TokenClaims) {
        if (claims.role != UserRole.admin && !claims.roles.contains(UserRole.admin)) {
            throw AccessDeniedException("Admin role required")
        }
    }

    /** GET /api/admin/users — list all users with optional filters */
    @GetMapping("/users")
    fun listUsers(
        @RequestParam role: String?,
        @RequestParam active: Boolean?,
        @RequestParam(defaultValue = "50") limit: Int,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        requireAdmin(claims)
        val userRole = role?.let { UserRole.valueOf(it) }
        val users = userRepository.findFiltered(userRole, active, PageRequest.of(0, limit))
        return ResponseEntity.ok(mapOf("users" to users.map { u ->
            mapOf(
                "id" to u.id.toString(),
                "email" to u.email,
                "name" to u.name,
                "role" to u.role.name,
                "active_role" to u.activeRole.name,
                "roles" to u.getRolesList().map { it.name },
                "active" to u.active,
                "location" to u.location,
                "rating" to u.rating,
                "jobs_completed" to u.jobsCompleted,
                "quality_score" to u.qualityScore,
                "created_at" to u.insertedAt?.toString()
            )
        }))
    }

    /** PUT /api/admin/users/{id}/status — suspend or activate a user */
    @PutMapping("/users/{id}/status")
    fun updateUserStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateUserStatusRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        requireAdmin(claims)
        val user = userRepository.findById(id)
            .orElseThrow { IllegalArgumentException("User not found: $id") }

        user.active = when (req.status) {
            "active" -> true
            "suspended" -> false
            else -> throw IllegalArgumentException("Status must be 'active' or 'suspended'")
        }
        val saved = userRepository.save(user)

        return ResponseEntity.ok(mapOf(
            "user" to mapOf(
                "id" to saved.id.toString(),
                "email" to saved.email,
                "name" to saved.name,
                "active" to saved.active
            )
        ))
    }

    /** GET /api/admin/disputes — list all disputes */
    @GetMapping("/disputes")
    fun listDisputes(
        @RequestParam status: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        requireAdmin(claims)
        val disputes = if (status != null) {
            disputeRepository.findByStatus(status)
        } else {
            disputeRepository.findAllRecent(PageRequest.of(0, limit))
        }
        return ResponseEntity.ok(mapOf("disputes" to disputes.map { d ->
            mapOf(
                "id" to d.id.toString(),
                "reason" to d.reason,
                "status" to d.status,
                "description" to d.description,
                "resolution_notes" to d.resolutionNotes,
                "job_id" to d.jobId?.toString(),
                "opened_by" to d.openedBy?.toString(),
                "contractor_id" to d.contractorId?.toString(),
                "homeowner_id" to d.homeownerId?.toString(),
                "resolved_by" to d.resolvedBy?.toString(),
                "opened_at" to d.openedAt?.toString(),
                "resolved_at" to d.resolvedAt?.toString(),
                "created_at" to d.insertedAt?.toString()
            )
        }))
    }

    /** POST /api/admin/disputes/{id}/resolve — resolve a dispute */
    @PostMapping("/disputes/{id}/resolve")
    fun resolveDispute(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ResolveDisputeRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        requireAdmin(claims)
        val dispute = disputeRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Dispute not found: $id") }

        require(dispute.status == "open") { "Dispute is already ${dispute.status}" }

        dispute.status = req.resolution
        dispute.resolutionNotes = req.resolution_notes
        dispute.resolvedBy = claims.userId
        dispute.resolvedAt = Instant.now()
        val saved = disputeRepository.save(dispute)

        return ResponseEntity.ok(mapOf("dispute" to mapOf(
            "id" to saved.id.toString(),
            "status" to saved.status,
            "resolution_notes" to saved.resolutionNotes,
            "resolved_by" to saved.resolvedBy?.toString(),
            "resolved_at" to saved.resolvedAt?.toString()
        )))
    }

    /** GET /api/admin/stats — platform-wide statistics */
    @GetMapping("/stats")
    fun stats(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        requireAdmin(claims)

        val totalUsers = userRepository.count()
        val homeowners = userRepository.countByRole(UserRole.homeowner)
        val contractors = userRepository.countByRole(UserRole.contractor)
        val subContractors = userRepository.countByRole(UserRole.sub_contractor)
        val totalJobs = jobRepository.count()
        val totalBids = bidRepository.count()
        val totalDisputes = disputeRepository.count()
        val openDisputes = disputeRepository.findByStatus("open").size.toLong()

        return ResponseEntity.ok(mapOf(
            "users" to mapOf(
                "total" to totalUsers,
                "homeowners" to homeowners,
                "contractors" to contractors,
                "sub_contractors" to subContractors
            ),
            "jobs" to mapOf("total" to totalJobs),
            "bids" to mapOf("total" to totalBids),
            "disputes" to mapOf(
                "total" to totalDisputes,
                "open" to openDisputes
            )
        ))
    }
}
