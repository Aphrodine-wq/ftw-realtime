package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * API key for tenant-scoped reads of the PriceGrid API.
 *
 * The visible key is `pgk_live_<32 random url-safe chars>`. We store only
 * sha256(full key) — exposing the raw key in the DB would leak access on
 * any backup. The first 8 chars of the random portion plus the prefix are
 * kept as `key_prefix` so humans can identify keys ("pgk_live_abcd...") in
 * the dashboard.
 */
@Entity
@Table(name = "api_keys")
class ApiKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    var tenantId: UUID = Tenant.DEFAULT_ID,

    @Column(nullable = false, length = 120)
    var name: String = "",

    @Column(name = "key_prefix", nullable = false, length = 24)
    var keyPrefix: String = "",

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    var keyHash: String = "",

    @Column(nullable = false, length = 20)
    var scope: String = "read",

    @Column(name = "rate_limit_per_hour")
    var rateLimitPerHour: Int? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "last_used_ip", length = 64)
    var lastUsedIp: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
) {
    companion object {
        private const val PREFIX = "pgk_live_"
        private val URLSAFE = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        /**
         * Generate a fresh API key. Returns (rawKey, hash, displayPrefix).
         * The raw key is shown to the user *once* — after that only the hash
         * exists in the DB.
         */
        fun mint(): Triple<String, String, String> {
            val rng = SecureRandom()
            val bytes = ByteArray(24)
            rng.nextBytes(bytes)
            val random = bytes.joinToString("") { URLSAFE[(it.toInt() and 0xFF) % URLSAFE.size].toString() }
            val raw = "$PREFIX$random"
            val hash = sha256Hex(raw)
            // pgk_live_ + first 4 of random for visible identification
            val prefix = "$PREFIX${random.substring(0, 4)}"
            return Triple(raw, hash, prefix)
        }

        fun sha256Hex(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val raw = md.digest(input.toByteArray(Charsets.UTF_8))
            return raw.joinToString("") { "%02x".format(it) }
        }
    }
}
