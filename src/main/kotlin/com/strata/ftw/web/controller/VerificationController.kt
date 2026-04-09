package com.strata.ftw.web.controller

import com.strata.ftw.service.FairTrustService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.SubmitVerificationRequest
import jakarta.validation.Valid
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
        @Valid @RequestBody req: SubmitVerificationRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val verification = fairTrust.submitVerification(claims.userId, step, req.data)
        return ResponseEntity.ok(mapOf("verification" to verification))
    }
}
