package com.strata.ftw.web.controller

import com.strata.ftw.service.FairTrustService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/contractor/verification")
class VerificationController(private val fairTrust: FairTrustService) {

    @GetMapping
    fun status(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val status = fairTrust.verificationStatus(claims.userId)
        return ResponseEntity.ok(status)
    }

    @PostMapping("/{step}")
    fun submitStep(
        @PathVariable step: String,
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val data = body["data"] as? Map<String, Any> ?: emptyMap()
        val verification = fairTrust.submitVerification(claims.userId, step, data)
        return ResponseEntity.ok(mapOf("verification" to verification))
    }
}
