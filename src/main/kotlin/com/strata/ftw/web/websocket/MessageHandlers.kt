package com.strata.ftw.web.websocket

import com.strata.ftw.domain.entity.*
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Controller
import java.security.Principal
import java.time.Instant
import java.util.UUID

@Controller
class MessageHandlers(
    private val marketplace: MarketplaceService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val subJobRepository: SubJobRepository,
    private val subBidRepository: SubBidRepository,
    private val userRepository: UserRepository
) {

    private fun claims(principal: Principal): TokenClaims =
        (principal as UsernamePasswordAuthenticationToken).principal as TokenClaims

    // ── Job Feed ──

    @MessageMapping("/jobs.feed.post")
    fun postJob(@Payload attrs: Map<String, Any>, principal: Principal) {
        val c = claims(principal)
        val job = marketplace.postJob(attrs, c.userId)
        // Broadcast handled by MarketplaceService
    }

    // ── Bids ──

    @MessageMapping("/job.{jobId}.bid")
    fun placeBid(
        @DestinationVariable jobId: String,
        @Payload attrs: Map<String, Any>,
        principal: Principal
    ) {
        val c = claims(principal)
        marketplace.placeBid(UUID.fromString(jobId), attrs, c.userId)
    }

    @MessageMapping("/job.{jobId}.accept")
    fun acceptBid(
        @DestinationVariable jobId: String,
        @Payload body: Map<String, String>,
        principal: Principal
    ) {
        val c = claims(principal)
        val bidId = UUID.fromString(body["bid_id"]!!)
        marketplace.acceptBid(UUID.fromString(jobId), bidId, c.userId)
    }

    // ── Chat ──

    @MessageMapping("/chat.{conversationId}.send")
    fun sendMessage(
        @DestinationVariable conversationId: String,
        @Payload body: Map<String, String>,
        principal: Principal
    ) {
        val c = claims(principal)
        marketplace.sendMessage(UUID.fromString(conversationId), body["body"]!!, c.userId)
    }

    @MessageMapping("/chat.{conversationId}.typing")
    fun typing(
        @DestinationVariable conversationId: String,
        @Payload body: Map<String, Boolean>,
        principal: Principal
    ) {
        val c = claims(principal)
        messagingTemplate.convertAndSend(
            "/topic/chat.$conversationId",
            mapOf("event" to "typing", "data" to mapOf("user_id" to c.userId.toString(), "typing" to (body["typing"] ?: true)))
        )
    }

    // ── Sub Jobs ──

    @MessageMapping("/sub-jobs.feed.post")
    fun postSubJob(@Payload attrs: Map<String, Any>, principal: Principal) {
        val c = claims(principal)
        val subJob = SubJob(
            contractorId = c.userId,
            projectId = attrs["project_id"]?.toString() ?: "",
            milestoneLabel = attrs["milestone_label"]?.toString() ?: "",
            milestoneIndex = (attrs["milestone_index"] as? Number)?.toInt() ?: 0,
            title = attrs["title"]?.toString() ?: "",
            description = attrs["description"]?.toString(),
            category = attrs["category"]?.toString(),
            skills = attrs["skills"]?.toString(),
            location = attrs["location"]?.toString(),
            budgetMin = (attrs["budget_min"] as? Number)?.toInt(),
            budgetMax = (attrs["budget_max"] as? Number)?.toInt(),
            paymentPath = try {
                SubPaymentPath.valueOf(attrs["payment_path"]?.toString() ?: "contractor_escrow")
            } catch (_: Exception) { SubPaymentPath.contractor_escrow },
            disclosedToOwner = attrs["disclosed_to_owner"] as? Boolean ?: false,
            deadline = (attrs["deadline"] as? String)?.let { Instant.parse(it) }
        )
        val saved = subJobRepository.save(subJob)
        messagingTemplate.convertAndSend(
            "/topic/sub-jobs.feed",
            mapOf("event" to "sub_job:posted", "data" to serializeSubJob(saved))
        )
    }

    @MessageMapping("/sub-job.{subJobId}.bid")
    fun placeSubBid(
        @DestinationVariable subJobId: String,
        @Payload attrs: Map<String, Any>,
        principal: Principal
    ) {
        val c = claims(principal)
        val subJobUuid = UUID.fromString(subJobId)
        val subJob = subJobRepository.findById(subJobUuid).orElse(null) ?: return

        val bid = SubBid(
            amount = (attrs["amount"] as? Number)?.toInt() ?: 0,
            message = attrs["message"]?.toString(),
            timeline = attrs["timeline"]?.toString()
        )
        bid.subJob = subJob
        bid.subContractor = userRepository.findById(c.userId).orElse(null)
        val saved = subBidRepository.save(bid)

        subJob.bidCount = subJob.bidCount + 1
        subJobRepository.save(subJob)

        messagingTemplate.convertAndSend(
            "/topic/sub-job.$subJobId",
            mapOf("event" to "sub_bid:placed", "data" to serializeSubBid(saved))
        )
    }

    private fun serializeSubJob(sj: SubJob): Map<String, Any?> = mapOf(
        "id" to sj.id.toString(),
        "contractor_id" to sj.contractorId.toString(),
        "project_id" to sj.projectId,
        "milestone_label" to sj.milestoneLabel,
        "title" to sj.title,
        "status" to sj.status.name,
        "bid_count" to sj.bidCount,
        "inserted_at" to sj.insertedAt?.toString()
    )

    private fun serializeSubBid(sb: SubBid): Map<String, Any?> = mapOf(
        "id" to sb.id.toString(),
        "sub_job_id" to sb.subJobId.toString(),
        "sub_contractor_id" to sb.subContractorId.toString(),
        "amount" to sb.amount,
        "status" to sb.status.name,
        "inserted_at" to sb.insertedAt?.toString()
    )
}
