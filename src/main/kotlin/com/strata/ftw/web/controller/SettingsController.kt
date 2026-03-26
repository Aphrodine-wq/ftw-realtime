package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/settings")
class SettingsController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun get(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val settings = marketplace.getSettings(claims.userId)
        return ResponseEntity.ok(mapOf("settings" to mapOf(
            "notifications_email" to settings.notificationsEmail,
            "notifications_push" to settings.notificationsPush,
            "notifications_sms" to settings.notificationsSms,
            "appearance_theme" to settings.appearanceTheme,
            "language" to settings.language,
            "timezone" to settings.timezone,
            "privacy_profile_visible" to settings.privacyProfileVisible,
            "privacy_show_rating" to settings.privacyShowRating
        )))
    }

    @PutMapping
    fun update(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["settings"] as? Map<String, Any> ?: body
        val settings = marketplace.updateSettings(claims.userId, attrs)
        return ResponseEntity.ok(mapOf("settings" to mapOf(
            "notifications_email" to settings.notificationsEmail,
            "notifications_push" to settings.notificationsPush,
            "notifications_sms" to settings.notificationsSms,
            "appearance_theme" to settings.appearanceTheme,
            "language" to settings.language,
            "timezone" to settings.timezone,
            "privacy_profile_visible" to settings.privacyProfileVisible,
            "privacy_show_rating" to settings.privacyShowRating
        )))
    }
}
