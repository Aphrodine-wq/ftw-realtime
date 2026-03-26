package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/clients")
class ClientController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val clients = marketplace.listClients(claims.userId)
        return ResponseEntity.ok(mapOf("clients" to clients.map { marketplace.serializeClient(it) }))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val client = marketplace.getClient(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("client" to marketplace.serializeClient(client)))
    }

    @PostMapping
    fun create(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["client"] as? Map<String, Any> ?: body
        val client = marketplace.createClient(attrs, claims.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("client" to marketplace.serializeClient(client)))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["client"] as? Map<String, Any> ?: body
        val client = marketplace.updateClient(id, attrs)
        return ResponseEntity.ok(mapOf("client" to marketplace.serializeClient(client)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteClient(id)
        return ResponseEntity.noContent().build()
    }
}
