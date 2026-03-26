package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/estimates")
class EstimateController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val estimates = marketplace.listEstimates(claims.userId)
        return ResponseEntity.ok(mapOf("estimates" to estimates.map { marketplace.serializeEstimate(it) }))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val estimate = marketplace.getEstimate(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("estimate" to marketplace.serializeEstimate(estimate)))
    }

    @PostMapping
    fun create(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["estimate"] as? Map<String, Any> ?: body
        val estimate = marketplace.createEstimate(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("estimate" to marketplace.serializeEstimate(estimate)))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["estimate"] as? Map<String, Any> ?: body
        val estimate = marketplace.updateEstimate(id, attrs)
        return ResponseEntity.ok(mapOf("estimate" to marketplace.serializeEstimate(estimate)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteEstimate(id)
        return ResponseEntity.noContent().build()
    }
}
