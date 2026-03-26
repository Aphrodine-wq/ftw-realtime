package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/push")
class PushController(private val marketplace: MarketplaceService) {

    @PostMapping("/register")
    fun register(
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val token = marketplace.registerPushToken(claims.userId, body["token"]!!, body["platform"]!!)
        return ResponseEntity.ok(mapOf("ok" to true, "id" to token.id.toString()))
    }

    @DeleteMapping("/unregister")
    fun unregister(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        marketplace.unregisterPushToken(body["token"]!!)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
