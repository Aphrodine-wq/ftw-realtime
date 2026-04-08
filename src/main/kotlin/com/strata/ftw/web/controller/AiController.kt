package com.strata.ftw.web.controller

import com.strata.ftw.ai.AiGateway
import com.strata.ftw.service.TokenClaims
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class AiController(private val aiGateway: AiGateway) {

    @PostMapping("/chat/stream")
    fun chatStream(
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims,
        response: HttpServletResponse
    ) {
        val message = body["message"] ?: throw IllegalArgumentException("message required")
        val conversationId = body["conversation_id"]

        response.contentType = "text/event-stream"
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Connection", "keep-alive")
        response.setHeader("X-Accel-Buffering", "no")

        val writer = response.writer

        aiGateway.chatStream(message, conversationId) { sseData ->
            writer.write(sseData)
            writer.flush()
        }

        writer.flush()
    }

    @GetMapping("/fair-price")
    fun fairPrice(
        @RequestParam category: String,
        @RequestParam zip: String,
        @RequestParam size: String
    ): ResponseEntity<Any> {
        val result = aiGateway.fairPrice(category, zip, size)
            ?: return ResponseEntity.ok(mapOf("error" to "not_found"))
        return ResponseEntity.ok(result)
    }

    @GetMapping("/stats")
    fun stats(): ResponseEntity<Any> {
        return ResponseEntity.ok(aiGateway.dailyStats())
    }

    @PostMapping("/chat")
    fun chat(
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val message = body["message"] ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "message required"))
        val conversationId = body["conversation_id"]
        val result = aiGateway.chat(message, conversationId)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/estimate")
    fun estimate(
        @RequestBody body: Map<String, String>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val description = body["description"] ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "description required"))
        val result = aiGateway.estimate(description)
        return ResponseEntity.ok(mapOf("estimate" to result, "raw" to null))
    }

    @PostMapping("/fair-scope")
    fun fairScope(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val category = body["category"] as? String ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "category required"))
        val title = body["title"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val areas = body["areas"] as? List<String> ?: emptyList()
        val materials = body["materials"] as? String

        val result = aiGateway.fairScope(category, title, areas, materials)
        return ResponseEntity.ok(result)
    }
}
