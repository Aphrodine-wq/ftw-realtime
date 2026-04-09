package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.UpdateSettingsRequest
import jakarta.validation.Valid
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
        @Valid @RequestBody req: UpdateSettingsRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>()
        req.notifications_email?.let { attrs["notifications_email"] = it }
        req.notifications_push?.let { attrs["notifications_push"] = it }
        req.notifications_sms?.let { attrs["notifications_sms"] = it }
        req.appearance_theme?.let { attrs["appearance_theme"] = it }
        req.language?.let { attrs["language"] = it }
        req.timezone?.let { attrs["timezone"] = it }
        req.privacy_profile_visible?.let { attrs["privacy_profile_visible"] = it }
        req.privacy_show_rating?.let { attrs["privacy_show_rating"] = it }
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
