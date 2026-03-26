package com.strata.ftw.web.filter

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
class RateLimitFilter : OncePerRequestFilter() {

    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(100_000)
        .build<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val ip = request.remoteAddr

        // Only rate limit auth and AI endpoints
        val limit = when {
            path.startsWith("/api/auth/login") -> 10L to Duration.ofSeconds(60)
            path.startsWith("/api/auth/register") -> 5L to Duration.ofSeconds(60)
            path.startsWith("/api/ai/") -> 60L to Duration.ofSeconds(60)
            else -> {
                filterChain.doFilter(request, response)
                return
            }
        }

        val key = "$ip:$path"
        val bucket = buckets.get(key) { _ ->
            Bucket.builder()
                .addLimit(Bandwidth.simple(limit.first, limit.second))
                .build()
        }!!

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            response.status = 429
            response.setHeader("Retry-After", "60")
            response.contentType = "application/json"
            response.writer.write("""{"error":"Rate limit exceeded"}""")
        }
    }
}
