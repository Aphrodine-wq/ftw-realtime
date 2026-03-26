package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/chat")
class ChatController(private val marketplace: MarketplaceService) {

    @GetMapping("/{conversationId}")
    fun listMessages(
        @PathVariable conversationId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        if (!marketplace.isConversationParticipant(conversationId, claims.userId)) {
            return ResponseEntity.status(403).body(mapOf("error" to "Not a participant"))
        }
        val messages = marketplace.listMessages(conversationId)
        return ResponseEntity.ok(mapOf("messages" to messages.map { marketplace.serializeMessage(it) }))
    }

    @PostMapping("/{conversationId}")
    fun sendMessage(
        @PathVariable conversationId: UUID,
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        if (!marketplace.isConversationParticipant(conversationId, claims.userId)) {
            return ResponseEntity.status(403).body(mapOf("error" to "Not a participant"))
        }
        @Suppress("UNCHECKED_CAST")
        val msgAttrs = body["message"] as? Map<String, Any> ?: body
        val message = marketplace.sendMessage(conversationId, msgAttrs["body"] as String, claims.userId)
        return ResponseEntity.ok(mapOf("message" to marketplace.serializeMessage(message)))
    }
}
