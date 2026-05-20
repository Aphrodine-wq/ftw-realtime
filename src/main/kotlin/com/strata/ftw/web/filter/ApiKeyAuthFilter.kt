package com.strata.ftw.web.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.service.ApiKeyResolveService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Resolves X-API-Key headers to a tenant and enforces rate limits.
 *
 * Permissive: requests without an API key fall through to the default
 * tenant (FTW's existing behavior). The actual DB lookup + rate limit
 * happens in ApiKeyResolveService — we keep this filter free of
 * @Transactional so OncePerRequestFilter can be wrapped by Tomcat without
 * CGLib proxy issues.
 */
@Component
class ApiKeyAuthFilter(
    private val resolveService: ApiKeyResolveService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        if (!path.startsWith("/api/v1/prices") && !path.startsWith("/api/v1/webhooks")) return true
        // The HMAC-protected ingest doesn't use API keys.
        if (path == "/api/v1/prices/ingest") return true
        return false
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val rawKey = request.getHeader("X-API-Key")?.trim()

        if (rawKey.isNullOrEmpty()) {
            // Anonymous → default tenant. Productized deploys can flip
            // this to require an API key.
            TenantContext.set(TenantContext.DEFAULT)
            try {
                chain.doFilter(request, response)
            } finally {
                TenantContext.clear()
            }
            return
        }

        val resolved = resolveService.resolve(rawKey, request.remoteAddr)
        if (resolved == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "invalid_api_key")
            return
        }

        if (!resolved.rateLimitOutcome.allowed) {
            response.setHeader("Retry-After", resolved.rateLimitOutcome.retryAfterSeconds.toString())
            response.setHeader("X-RateLimit-Limit", resolved.rateLimitOutcome.limit.toString())
            response.setHeader("X-RateLimit-Remaining", "0")
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limited")
            return
        }

        response.setHeader("X-RateLimit-Limit", resolved.rateLimitOutcome.limit.toString())
        response.setHeader("X-RateLimit-Remaining", resolved.rateLimitOutcome.remaining.toString())

        TenantContext.set(resolved.tenantInfo)
        try {
            chain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun writeError(response: HttpServletResponse, status: HttpStatus, code: String) {
        response.status = status.value()
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(mapOf("error" to code)))
        response.writer.flush()
    }
}
