package com.strata.ftw.web.websocket

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.UUID

@Controller
class MessageHandlers(
    private val marketplace: MarketplaceService,
    private val messagingTemplate: SimpMessagingTemplate
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
}
