package com.strata.ftw.web.controller

import com.strata.ftw.domain.repository.ConversationRepository
import com.strata.ftw.domain.repository.MessageRepository
import com.strata.ftw.domain.repository.UserRepository
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
class ChatController(
    private val marketplace: MarketplaceService,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/conversations")
    fun listConversations(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val conversations = conversationRepository.findByParticipant(claims.userId)
        val result = conversations.map { conv ->
            val participantId = if (conv.homeownerId == claims.userId) conv.contractorId else conv.homeownerId
            val participant = participantId?.let { userRepository.findById(it).orElse(null) }
            val messages = messageRepository.findByConversationIdOrderByInsertedAtAsc(conv.id!!)
            val lastMessage = messages.lastOrNull()
            val unreadCount = 0 // TODO: track read cursors per user
            mapOf(
                "id" to conv.id.toString(),
                "participant_name" to (participant?.name ?: "Unknown"),
                "participant_id" to participantId?.toString(),
                "last_message" to lastMessage?.body,
                "last_message_at" to (lastMessage?.insertedAt ?: conv.updatedAt)?.toString(),
                "unread_count" to unreadCount
            )
        }
        return ResponseEntity.ok(mapOf("conversations" to result))
    }

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
