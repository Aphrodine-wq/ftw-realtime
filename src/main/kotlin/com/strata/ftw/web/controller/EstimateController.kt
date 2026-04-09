package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
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
        @Valid @RequestBody req: CreateEstimateRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>(
            "title" to req.title,
            "total" to req.total
        )
        req.description?.let { attrs["description"] = it }
        req.notes?.let { attrs["notes"] = it }
        req.client_id?.let { attrs["client_id"] = it }
        req.job_id?.let { attrs["job_id"] = it }
        if (req.line_items.isNotEmpty()) {
            attrs["line_items"] = req.line_items.map { li ->
                mapOf<String, Any?>(
                    "description" to li.description,
                    "quantity" to li.quantity,
                    "unit" to li.unit,
                    "unit_price" to li.unit_price,
                    "total" to li.total,
                    "category" to li.category
                ).filterValues { it != null }
            }
        }
        val estimate = marketplace.createEstimate(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("estimate" to marketplace.serializeEstimate(estimate)))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateEstimateRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>()
        req.title?.let { attrs["title"] = it }
        req.description?.let { attrs["description"] = it }
        req.total?.let { attrs["total"] = it }
        req.notes?.let { attrs["notes"] = it }
        req.status?.let { attrs["status"] = it }
        val estimate = marketplace.updateEstimate(id, attrs)
        return ResponseEntity.ok(mapOf("estimate" to marketplace.serializeEstimate(estimate)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteEstimate(id)
        return ResponseEntity.noContent().build()
    }
}
