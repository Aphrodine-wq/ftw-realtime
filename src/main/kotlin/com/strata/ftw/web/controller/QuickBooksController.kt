package com.strata.ftw.web.controller

import com.strata.ftw.service.QuickBooksException
import com.strata.ftw.service.QuickBooksService
import com.strata.ftw.service.TokenClaims
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/quickbooks")
class QuickBooksController(
    private val quickBooksService: QuickBooksService,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(QuickBooksController::class.java)

    /**
     * OAuth callback — Intuit redirects here after contractor authorizes.
     * The frontend initiates the OAuth flow and passes state containing the JWT.
     * On success, redirects to frontend with success flag.
     */
    @GetMapping("/callback")
    fun oauthCallback(
        @RequestParam("code") code: String,
        @RequestParam("realmId") realmId: String,
        @RequestParam("state", required = false) state: String?
    ): ResponseEntity<Any> {
        // State contains the user JWT — the frontend sets this during OAuth initiation
        // For the callback, we need to extract the user ID from the state
        // If state is missing, redirect with error
        if (state.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "$frontendUrl/settings/integrations?qb=error&reason=missing_state")
                .build()
        }

        return try {
            // State is "userId:<uuid>" set by the frontend before redirecting to Intuit
            val userId = UUID.fromString(state.removePrefix("userId:"))
            val credential = quickBooksService.exchangeCodeForTokens(code, realmId, userId)

            log.info("QB connected for user {} — realm {} ({})", userId, realmId, credential.companyName)

            ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "$frontendUrl/settings/integrations?qb=connected&company=${credential.companyName ?: ""}")
                .build()
        } catch (e: Exception) {
            log.error("QB OAuth callback failed: {}", e.message, e)
            ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "$frontendUrl/settings/integrations?qb=error&reason=${e.message}")
                .build()
        }
    }

    /**
     * Check if the current contractor has QuickBooks connected.
     */
    @GetMapping("/status")
    fun status(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val credential = quickBooksService.getCredentials(claims.userId)
        return ResponseEntity.ok(mapOf(
            "connected" to (credential != null),
            "company_name" to credential?.companyName,
            "realm_id" to credential?.realmId,
            "connected_at" to credential?.insertedAt
        ))
    }

    /**
     * Disconnect QuickBooks — revoke tokens and remove credentials.
     */
    @DeleteMapping("/disconnect")
    fun disconnect(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        quickBooksService.disconnect(claims.userId)
        log.info("QB disconnected for user {}", claims.userId)
        return ResponseEntity.ok(mapOf("disconnected" to true))
    }

    /**
     * Sync an FTW invoice to QuickBooks Online.
     * Creates a corresponding invoice in the contractor's QB company.
     */
    @PostMapping("/invoices/{invoiceId}/sync")
    fun syncInvoice(
        @PathVariable invoiceId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val invoice = quickBooksService.createQbInvoice(invoiceId, claims.userId)
            ResponseEntity.ok(mapOf(
                "invoice_id" to invoice.id,
                "qb_invoice_id" to invoice.qbInvoiceId,
                "qb_synced_at" to invoice.qbSyncedAt,
                "status" to invoice.status
            ))
        } catch (e: QuickBooksException) {
            log.error("QB invoice sync failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf(
                "error" to "quickbooks_api_error",
                "message" to e.message
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to "not_connected",
                "message" to e.message
            ))
        }
    }

    /**
     * Record a payment against a QB-synced invoice.
     * Marks the FTW invoice as paid and creates a Payment in QuickBooks.
     */
    @PostMapping("/invoices/{invoiceId}/payment")
    fun recordPayment(
        @PathVariable invoiceId: UUID,
        @RequestBody(required = false) body: Map<String, Any>?,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val paymentAmount = (body?.get("amount") as? Number)?.toInt()
            val invoice = quickBooksService.recordPayment(invoiceId, claims.userId, paymentAmount)
            ResponseEntity.ok(mapOf(
                "invoice_id" to invoice.id,
                "status" to invoice.status,
                "paid_at" to invoice.paidAt
            ))
        } catch (e: QuickBooksException) {
            log.error("QB payment recording failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf(
                "error" to "quickbooks_api_error",
                "message" to e.message
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to "invoice_not_synced",
                "message" to e.message
            ))
        }
    }

    /**
     * Fetch the QB invoice data for an already-synced invoice.
     */
    @GetMapping("/invoices/{invoiceId}")
    fun getQbInvoice(
        @PathVariable invoiceId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        return try {
            val qbData = quickBooksService.getQbInvoice(invoiceId, claims.userId)
            ResponseEntity.ok(qbData)
        } catch (e: QuickBooksException) {
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf(
                "error" to "quickbooks_api_error",
                "message" to e.message
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to "invoice_not_synced",
                "message" to e.message
            ))
        }
    }
}
