package com.strata.ftw.config

import com.strata.ftw.web.filter.ApiKeyAuthFilter
import com.strata.ftw.web.filter.JwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                headers
                    .contentTypeOptions { }
                    .frameOptions { it.deny() }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                    .httpStrictTransportSecurity { it.maxAgeInSeconds(63072000).includeSubDomains(true) }
                    .permissionsPolicy { it.policy("camera=(), microphone=(), geolocation=(self)") }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/google", "/api/auth/apple").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/sub-jobs", "/api/sub-jobs/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/ai/fair-price", "/api/ai/stats").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/records/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/records/*/certificate").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                    // QuickBooks OAuth callback (Intuit redirects browser here)
                    .requestMatchers(HttpMethod.GET, "/api/quickbooks/callback").permitAll()
                    // QuickBooks webhook (Intuit posts here — verified via HMAC)
                    .requestMatchers(HttpMethod.POST, "/api/quickbooks/webhook").permitAll()
                    // Material price ingest from ftw-scraper — verified via HMAC in controller
                    .requestMatchers(HttpMethod.POST, "/api/v1/prices/ingest").permitAll()
                    // Public price reads (current prices, material detail) — no auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/prices/**").permitAll()
                    // Bulk lookup is a POST but it's still read-only (no auth)
                    .requestMatchers(HttpMethod.POST, "/api/v1/prices/lookup").permitAll()
                    // OpenAPI / Swagger UI for the public price API
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Webhook subscription management — productization will gate via tenant API key
                    .requestMatchers("/api/v1/webhooks/**").permitAll()
                    // Admin endpoints — call from a trusted backend (buyer's dashboard) only
                    .requestMatchers("/api/v1/admin/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/switch-role").authenticated()
                    // Admin endpoints — role check handled in controller
                    .requestMatchers("/api/admin/**").authenticated()
                    // WebSocket endpoint
                    .requestMatchers("/ws/**").permitAll()
                    // Everything else requires auth
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            // API key filter runs before JWT — for /api/v1/prices and /api/v1/webhooks
            // it resolves tenant context from X-API-Key. JWT filter is no-op there.
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = this@SecurityConfig.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 86400
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
