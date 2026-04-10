package com.strata.ftw.web

import com.strata.ftw.service.QuickBooksException
import com.strata.ftw.service.QuickBooksTokenException
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, "validation_error", errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "bad_request", ex.message ?: "Invalid request")

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.CONFLICT, "conflict", ex.message ?: "Operation not allowed in current state")

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "not_found", ex.message ?: "Resource not found")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.FORBIDDEN, "forbidden", ex.message ?: "Access denied")

    @ExceptionHandler(QuickBooksTokenException::class)
    fun handleQuickBooksToken(ex: QuickBooksTokenException): ResponseEntity<Map<String, Any>> {
        log.error("QuickBooks token error: {}", ex.message)
        return error(HttpStatus.UNAUTHORIZED, "quickbooks_auth_error", ex.message ?: "QuickBooks authorization expired. Please reconnect.")
    }

    @ExceptionHandler(QuickBooksException::class)
    fun handleQuickBooks(ex: QuickBooksException): ResponseEntity<Map<String, Any>> {
        log.error("QuickBooks API error: {}", ex.message)
        return error(HttpStatus.BAD_GATEWAY, "quickbooks_error", ex.message ?: "QuickBooks API error")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, Any>> {
        log.error("Unhandled exception", ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred")
    }

    private fun error(status: HttpStatus, error: String, message: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(mapOf(
            "error" to error,
            "message" to message,
            "status" to status.value(),
            "timestamp" to Instant.now().toString()
        ))
}
