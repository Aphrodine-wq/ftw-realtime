package com.strata.ftw.web.filter

import com.strata.ftw.service.AuthService
import com.strata.ftw.service.TokenClaims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val authService: AuthService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)
        if (token != null) {
            val claims = authService.verifyToken(token)
            if (claims != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    claims,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${claims.role.name.uppercase()}"))
                )
                SecurityContextHolder.getContext().authentication = auth

                // Set attributes for easy access in controllers
                request.setAttribute("current_user_id", claims.userId)
                request.setAttribute("current_email", claims.email)
                request.setAttribute("current_role", claims.role)
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        return if (header != null && header.startsWith("Bearer ")) {
            header.substring(7)
        } else null
    }
}
