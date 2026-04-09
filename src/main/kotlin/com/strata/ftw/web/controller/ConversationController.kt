package com.strata.ftw.web.controller

import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/conversations")
class ConversationController(private val chatController: ChatController) {

    @GetMapping
    fun listConversations(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> =
        chatController.listConversations(claims)
}
