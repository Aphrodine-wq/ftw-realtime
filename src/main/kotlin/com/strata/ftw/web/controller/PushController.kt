package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/push")
class PushController(private val marketplace: MarketplaceService) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody req: RegisterPushTokenRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val token = marketplace.registerPushToken(claims.userId, req.token, req.platform)
        return ResponseEntity.ok(mapOf("ok" to true, "id" to token.id.toString()))
    }

    @DeleteMapping("/unregister")
    fun unregister(@Valid @RequestBody req: UnregisterPushTokenRequest): ResponseEntity<Any> {
        marketplace.unregisterPushToken(req.token)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
