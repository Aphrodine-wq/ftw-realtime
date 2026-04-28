package com.strata.ftw.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URL
import java.util.Date

data class OAuthProfile(
    val email: String,
    val name: String,
    val emailVerified: Boolean,
    val providerSubId: String,
)

@Service
class OAuthService(
    @Value("\${oauth.google.client-id:}") private val googleClientId: String,
    @Value("\${oauth.apple.client-id:}") private val appleClientId: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val googleVerifier: GoogleIdTokenVerifier? by lazy {
        if (googleClientId.isBlank()) {
            log.warn("[oauth] GOOGLE_CLIENT_ID not set — Google sign-in disabled")
            null
        } else {
            GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(listOf(googleClientId))
                .build()
        }
    }

    private val appleProcessor: DefaultJWTProcessor<SecurityContext>? by lazy {
        if (appleClientId.isBlank()) {
            log.warn("[oauth] APPLE_CLIENT_ID not set — Apple sign-in disabled")
            null
        } else {
            DefaultJWTProcessor<SecurityContext>().apply {
                val jwkSet = RemoteJWKSet<SecurityContext>(URL(APPLE_JWKS_URL))
                jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSet)
                jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                    JWTClaimsSet.Builder()
                        .issuer(APPLE_ISSUER)
                        .audience(appleClientId)
                        .build(),
                    setOf("sub", "iat", "exp"),
                )
            }
        }
    }

    fun verifyGoogle(idToken: String): OAuthProfile? {
        val verifier = googleVerifier ?: return null
        return try {
            val token = verifier.verify(idToken) ?: return null
            val payload = token.payload
            val email = payload.email ?: return null
            if (payload.emailVerified != true) {
                log.warn("[oauth] google: email not verified: $email")
                return null
            }
            OAuthProfile(
                email = email.lowercase(),
                name = (payload["name"] as? String)?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                emailVerified = true,
                providerSubId = payload.subject,
            )
        } catch (e: Exception) {
            log.warn("[oauth] google verify failed: ${e.message}")
            null
        }
    }

    /**
     * Apple's id_token does not always include name (only on first sign-in via the JS callback).
     * Pass the user-provided name from the client when available; otherwise we fall back to
     * the email's local part.
     */
    fun verifyApple(idToken: String, fallbackName: String? = null): OAuthProfile? {
        val processor = appleProcessor ?: return null
        return try {
            val signed = SignedJWT.parse(idToken)
            val claims = processor.process(signed, null)
            val email = claims.getStringClaim("email") ?: return null
            val emailVerified = when (val v = claims.getClaim("email_verified")) {
                is Boolean -> v
                is String -> v.equals("true", ignoreCase = true)
                else -> false
            }
            val expiry = claims.expirationTime
            if (expiry != null && expiry.before(Date())) {
                log.warn("[oauth] apple: token expired")
                return null
            }
            OAuthProfile(
                email = email.lowercase(),
                name = fallbackName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                emailVerified = emailVerified,
                providerSubId = claims.subject,
            )
        } catch (e: Exception) {
            log.warn("[oauth] apple verify failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys"
        private const val APPLE_ISSUER = "https://appleid.apple.com"
    }
}
