package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/notifications")
class NotificationController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val notifications = marketplace.listNotifications(claims.userId)
        return ResponseEntity.ok(mapOf("notifications" to notifications.map { marketplace.serializeNotification(it) }))
    }

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.markNotificationRead(id)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/read-all")
    fun markAllRead(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        marketplace.markAllNotificationsRead(claims.userId)
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
