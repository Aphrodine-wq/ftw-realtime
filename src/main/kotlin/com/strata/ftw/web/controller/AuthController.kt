package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.UserRole
import com.strata.ftw.service.AuthService
import com.strata.ftw.service.EmailService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val emailService: EmailService,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
) {

    /** Parse role string accepting any case and "subcontractor" as alias for "sub_contractor" */
    private fun parseRole(role: String): UserRole {
        val normalized = role.lowercase().replace("-", "_")
        val mapped = if (normalized == "subcontractor") "sub_contractor" else normalized
        return UserRole.valueOf(mapped)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): ResponseEntity<Any> {
        val user = authService.authenticate(req.email, req.password)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid credentials"))

        val token = authService.generateToken(user)
        return ResponseEntity.ok(mapOf(
            "token" to token,
            "user" to mapOf(
                "id" to user.id.toString(),
                "email" to user.email,
                "name" to user.name,
                "role" to user.activeRole.name,
                "roles" to user.getRolesList().map { it.name }
            )
        ))
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): ResponseEntity<Any> {
        val role = parseRole(req.role)
        val user = authService.registerUser(req.email, req.password, req.name, role, req.location)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
            "user" to mapOf(
                "id" to user.id.toString(),
                "email" to user.email,
                "name" to user.name,
                "role" to user.activeRole.name,
                "roles" to user.getRolesList().map { it.name }
            )
        ))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        return ResponseEntity.ok(mapOf(
            "user" to mapOf(
                "id" to claims.userId.toString(),
                "email" to claims.email,
                "role" to claims.role.name,
                "roles" to claims.roles.map { it.name }
            )
        ))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody req: ForgotPasswordRequest): ResponseEntity<Any> {
        val user = authService.findByEmail(req.email)
        if (user != null) {
            val resetToken = authService.generateResetToken(user)
            val resetUrl = "$frontendUrl/reset-password?token=$resetToken"
            emailService.sendPasswordReset(user.email, user.name, resetUrl)
        }
        return ResponseEntity.ok(mapOf("message" to "If an account exists, a reset link has been sent."))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody req: ResetPasswordRequest): ResponseEntity<Any> {
        val userId = authService.verifyResetToken(req.token)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid or expired reset token"))

        authService.resetPassword(userId, req.password)
        return ResponseEntity.ok(mapOf("message" to "Password reset successfully"))
    }

    @PostMapping("/switch-role")
    fun switchRole(
        @Valid @RequestBody req: SwitchRoleRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val targetRole = parseRole(req.role)
        val user = authService.switchRole(claims.userId, targetRole)
        val token = authService.generateToken(user)
        return ResponseEntity.ok(mapOf(
            "token" to token,
            "user" to mapOf(
                "id" to user.id.toString(),
                "email" to user.email,
                "name" to user.name,
                "role" to user.activeRole.name,
                "roles" to user.getRolesList().map { it.name }
            )
        ))
    }
}
