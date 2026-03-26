package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.UserRole
import com.strata.ftw.service.AuthService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    data class LoginRequest(val email: String, val password: String)
    data class RegisterRequest(
        val email: String,
        val password: String,
        val name: String,
        val role: String,
        val location: String? = null
    )

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<Any> {
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
                "role" to user.role.name
            )
        ))
    }

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<Any> {
        return try {
            val role = UserRole.valueOf(req.role)
            val user = authService.registerUser(req.email, req.password, req.name, role, req.location)
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                "user" to mapOf(
                    "id" to user.id.toString(),
                    "email" to user.email,
                    "name" to user.name,
                    "role" to user.role.name
                )
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Registration failed")))
        }
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        return ResponseEntity.ok(mapOf(
            "user" to mapOf(
                "id" to claims.userId.toString(),
                "email" to claims.email,
                "role" to claims.role.name
            )
        ))
    }
}
