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
@RequestMapping("/api/invoices")
class InvoiceController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val invoices = marketplace.listInvoices(claims.userId)
        return ResponseEntity.ok(mapOf("invoices" to invoices.map { marketplace.serializeInvoice(it) }))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val invoice = marketplace.getInvoice(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("invoice" to marketplace.serializeInvoice(invoice)))
    }

    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateInvoiceRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>(
            "invoice_number" to req.invoice_number,
            "amount" to req.amount
        )
        req.notes?.let { attrs["notes"] = it }
        req.due_date?.let { attrs["due_date"] = it }
        req.client_id?.let { attrs["client_id"] = it }
        req.estimate_id?.let { attrs["estimate_id"] = it }
        req.project_id?.let { attrs["project_id"] = it }
        val invoice = marketplace.createInvoice(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("invoice" to marketplace.serializeInvoice(invoice)))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateInvoiceRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>()
        req.amount?.let { attrs["amount"] = it }
        req.status?.let { attrs["status"] = it }
        req.notes?.let { attrs["notes"] = it }
        req.due_date?.let { attrs["due_date"] = it }
        val invoice = marketplace.updateInvoice(id, attrs)
        return ResponseEntity.ok(mapOf("invoice" to marketplace.serializeInvoice(invoice)))
    }
}
