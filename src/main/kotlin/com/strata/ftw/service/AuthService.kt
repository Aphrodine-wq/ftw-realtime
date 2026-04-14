package com.strata.ftw.service

import com.strata.ftw.domain.entity.User
import com.strata.ftw.domain.entity.UserRole
import com.strata.ftw.domain.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class AuthService(
    private val userRepository: UserRepository,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.ttl-seconds}") private val jwtTtlSeconds: Long,
    @Value("\${app.jwt.issuer}") private val issuer: String,
    @Value("\${app.jwt.audience}") private val audience: String
) {
    // Argon2id encoder matching Elixir's argon2_elixir defaults
    private val passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
    private val bcryptEncoder = BCryptPasswordEncoder()

    private fun signingKey(): SecretKey =
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())

    fun generateToken(user: User): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("user_id", user.id.toString())
            .claim("email", user.email)
            .claim("role", user.activeRole.name)
            .claim("roles", user.getRolesList().map { it.name })
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtTtlSeconds)))
            .signWith(signingKey())
            .compact()
    }

    fun verifyToken(token: String): TokenClaims? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            val rolesList = try {
                @Suppress("UNCHECKED_CAST")
                (claims["roles"] as? List<String>)?.map { UserRole.valueOf(it) } ?: listOf()
            } catch (_: Exception) { listOf() }

            TokenClaims(
                userId = UUID.fromString(claims["user_id"] as String),
                email = claims["email"] as String,
                role = UserRole.valueOf(claims["role"] as String),
                roles = rolesList
            )
        } catch (_: Exception) {
            null
        }
    }

    fun authenticate(email: String, password: String): User? {
        val user = userRepository.findByEmail(email) ?: return null
        val hash = user.passwordHash ?: return null

        // Handle both Argon2 (from Elixir) and bcrypt formats
        return if (verifyPassword(password, hash)) user else null
    }

    fun registerUser(email: String, password: String, name: String, role: UserRole, location: String? = null): User {
        val user = User(
            email = email,
            name = name,
            role = role,
            activeRole = role,
            roles = role.name,
            location = location,
            passwordHash = hashPassword(password)
        )
        return userRepository.save(user)
    }

    fun switchRole(userId: UUID, targetRole: UserRole): User {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        if (!user.hasRole(targetRole)) {
            throw IllegalArgumentException("User does not have role: ${targetRole.name}")
        }
        user.activeRole = targetRole
        return userRepository.save(user)
    }

    fun generateResetToken(user: User): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("user_id", user.id.toString())
            .claim("email", user.email)
            .claim("type", "reset")
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900))) // 15 minutes
            .signWith(signingKey())
            .compact()
    }

    fun verifyResetToken(token: String): UUID? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            val type = claims["type"] as? String
            if (type != "reset") return null

            UUID.fromString(claims["user_id"] as String)
        } catch (_: Exception) {
            null
        }
    }

    fun resetPassword(userId: UUID, newPassword: String): User {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        user.passwordHash = hashPassword(newPassword)
        return userRepository.save(user)
    }

    fun findByEmail(email: String): User? = userRepository.findByEmail(email)

    fun findById(userId: UUID): User? = userRepository.findById(userId).orElse(null)

    fun activateSubContractorRole(userId: UUID): User {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        user.addRole(UserRole.sub_contractor)
        return userRepository.save(user)
    }

    fun hashPassword(password: String): String =
        passwordEncoder.encode(password)

    private fun verifyPassword(rawPassword: String, encodedPassword: String): Boolean {
        return try {
            if (encodedPassword.startsWith("\$2a\$") || encodedPassword.startsWith("\$2b\$")) {
                bcryptEncoder.matches(rawPassword, encodedPassword)
            } else {
                passwordEncoder.matches(rawPassword, encodedPassword)
            }
        } catch (_: Exception) {
            false
        }
    }
}

data class TokenClaims(
    val userId: UUID,
    val email: String,
    val role: UserRole,
    val roles: List<UserRole> = listOf()
)
