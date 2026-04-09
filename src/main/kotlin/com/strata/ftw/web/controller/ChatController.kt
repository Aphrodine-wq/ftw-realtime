package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.SendMessageRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
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
            throw AccessDeniedException("Not a participant in this conversation")
        }
        val messages = marketplace.listMessages(conversationId)
        return ResponseEntity.ok(mapOf("messages" to messages.map { marketplace.serializeMessage(it) }))
    }

    @PostMapping("/{conversationId}")
    fun sendMessage(
        @PathVariable conversationId: UUID,
        @Valid @RequestBody req: SendMessageRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        if (!marketplace.isConversationParticipant(conversationId, claims.userId)) {
            throw AccessDeniedException("Not a participant in this conversation")
        }
        val message = marketplace.sendMessage(conversationId, req.body, claims.userId)
        return ResponseEntity.ok(mapOf("message" to marketplace.serializeMessage(message)))
    }
}
