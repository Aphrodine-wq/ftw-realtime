package com.strata.ftw.web.controller

import com.strata.ftw.service.FairRecordPdfService
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class FairRecordController(
    private val marketplace: MarketplaceService,
    private val pdfService: FairRecordPdfService
) {

    @GetMapping("/api/records/{publicId}")
    fun getByPublicId(@PathVariable publicId: String): ResponseEntity<Any> {
        val record = marketplace.getFairRecordByPublicId(publicId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("record" to marketplace.serializeFairRecord(record)))
    }

    @GetMapping("/api/records/{publicId}/certificate")
    fun certificate(@PathVariable publicId: String): ResponseEntity<String> {
        val record = marketplace.getFairRecordByPublicId(publicId)
            ?: return ResponseEntity.notFound().build()
        val html = pdfService.generateCertificateHtml(record)
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)
    }

    @GetMapping("/api/contractors/{contractorId}/records")
    fun listByContractor(@PathVariable contractorId: UUID): ResponseEntity<Any> {
        val records = marketplace.listContractorRecords(contractorId)
        val stats = marketplace.contractorRecordStats(contractorId)
        return ResponseEntity.ok(mapOf(
            "records" to records.map { marketplace.serializeFairRecord(it) },
            "stats" to stats
        ))
    }

    @PostMapping("/api/records/{recordId}/confirm")
    fun confirm(
        @PathVariable recordId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val record = marketplace.confirmFairRecord(recordId, claims.userId)
            ResponseEntity.ok(mapOf("record" to marketplace.serializeFairRecord(record)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
