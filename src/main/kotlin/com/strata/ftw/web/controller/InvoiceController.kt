package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
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
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["invoice"] as? Map<String, Any> ?: body
        val invoice = marketplace.createInvoice(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("invoice" to marketplace.serializeInvoice(invoice)))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["invoice"] as? Map<String, Any> ?: body
        val invoice = marketplace.updateInvoice(id, attrs)
        return ResponseEntity.ok(mapOf("invoice" to marketplace.serializeInvoice(invoice)))
    }
}
