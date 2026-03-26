package com.strata.ftw.web.controller

import com.strata.ftw.service.FairTrustService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks")
class WebhookController(private val fairTrust: FairTrustService) {

    @PostMapping("/persona")
    fun personaWebhook(
        @RequestBody body: Map<String, Any>,
        @RequestHeader("Persona-Signature") signature: String?
    ): ResponseEntity<Any> {
        fairTrust.handlePersonaWebhook(body, signature)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/checkr")
    fun checkrWebhook(
        @RequestBody body: Map<String, Any>,
        @RequestHeader("X-Checkr-Signature") signature: String?
    ): ResponseEntity<Any> {
        fairTrust.handleCheckrWebhook(body, signature)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
