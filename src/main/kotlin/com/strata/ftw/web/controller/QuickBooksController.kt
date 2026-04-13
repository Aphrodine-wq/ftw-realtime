package com.strata.ftw.web.controller

import com.strata.ftw.service.QuickBooksService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
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
     * State contains "userId:<uuid>" set by frontend before redirect to Intuit.
     */
    @GetMapping("/callback")
    fun oauthCallback(
        @RequestParam("code") code: String,
        @RequestParam("realmId") realmId: String,
        @RequestParam("state", required = false) state: String?
    ): ResponseEntity<Any> {
        if (state.isNullOrBlank()) {
            return redirectToFrontend("error", "missing_state")
        }

        return try {
            val userId = UUID.fromString(state.removePrefix("userId:"))
            val credential = quickBooksService.exchangeCodeForTokens(code, realmId, userId)
            log.info("QB connected for user {} — realm {} ({})", userId, realmId, credential.companyName)
            redirectToFrontend("connected", credential.companyName)
        } catch (e: IllegalArgumentException) {
            log.error("QB OAuth callback — invalid state: {}", state)
            redirectToFrontend("error", "invalid_state")
        } catch (e: Exception) {
            log.error("QB OAuth callback failed: {}", e.message, e)
            redirectToFrontend("error", "token_exchange_failed")
        }
    }

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

    @DeleteMapping("/disconnect")
    fun disconnect(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        quickBooksService.disconnect(claims.userId)
        log.info("QB disconnected for user {}", claims.userId)
        return ResponseEntity.ok(mapOf("disconnected" to true))
    }

    /**
     * Sync an FTW invoice to QuickBooks Online.
     * Idempotent — re-syncing an already-synced invoice returns the existing QB data.
     */
    @PostMapping("/invoices/{invoiceId}/sync")
    fun syncInvoice(
        @PathVariable invoiceId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val invoice = quickBooksService.createQbInvoice(invoiceId, claims.userId)
        return ResponseEntity.ok(mapOf(
            "invoice_id" to invoice.id,
            "qb_invoice_id" to invoice.qbInvoiceId,
            "qb_synced_at" to invoice.qbSyncedAt,
            "status" to invoice.status
        ))
    }

    /**
     * Record a payment against a QB-synced invoice.
     */
    @PostMapping("/invoices/{invoiceId}/payment")
    fun recordPayment(
        @PathVariable invoiceId: UUID,
        @Valid @RequestBody(required = false) body: RecordPaymentRequest?,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val invoice = quickBooksService.recordPayment(invoiceId, claims.userId, body?.amount)
        return ResponseEntity.ok(mapOf(
            "invoice_id" to invoice.id,
            "status" to invoice.status,
            "paid_at" to invoice.paidAt
        ))
    }

    /**
     * Fetch the QB invoice data for an already-synced invoice.
     */
    @GetMapping("/invoices/{invoiceId}")
    fun getQbInvoice(
        @PathVariable invoiceId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val qbData = quickBooksService.getQbInvoice(invoiceId, claims.userId)
        return ResponseEntity.ok(qbData)
    }

    // ── Create Invoice from Bid ──

    @PostMapping("/create-invoice")
    fun createInvoice(
        @Valid @RequestBody body: CreateQbInvoiceRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val result = quickBooksService.createInvoiceFromBid(
            bidId = body.bidId,
            userId = claims.userId,
            milestone = body.milestone,
            dueDate = body.dueDate,
            customerName = body.customerName,
            customerEmail = body.customerEmail,
            description = body.description
        )
        return ResponseEntity.ok(result)
    }

    // ── Sync Estimate to QB ──

    @PostMapping("/sync-estimate")
    fun syncEstimate(
        @Valid @RequestBody body: SyncEstimateRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val lineItems = body.lineItems.map { item ->
            mapOf(
                "description" to item.description,
                "quantity" to item.quantity,
                "unitPrice" to item.unitPrice
            )
        }
        val result = quickBooksService.syncEstimate(
            userId = claims.userId,
            customerName = body.customerName,
            customerEmail = body.customerEmail,
            lineItems = lineItems,
            expirationDate = body.expirationDate,
            title = body.title
        )
        return ResponseEntity.ok(result)
    }

    // ── Payout ──

    @GetMapping("/payout")
    fun getPayout(
        @RequestParam bidId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val payout = quickBooksService.getPayout(bidId)
        // Money fields (grossAmount, platformFee, netAmount) are in cents — divide by 100 for display
        return ResponseEntity.ok(mapOf(
            "id" to payout.id,
            "bidId" to payout.bidId,
            "grossAmount" to payout.grossAmount, // cents
            "platformFee" to payout.platformFee, // cents
            "netAmount" to payout.netAmount, // cents
            "feePercent" to payout.feePercent,
            "status" to payout.status.name,
            "failureReason" to payout.failureReason,
            "paidAt" to payout.paidAt,
            "createdAt" to payout.insertedAt
        ))
    }

    @PostMapping("/payout")
    fun executePayout(
        @Valid @RequestBody body: ExecutePayoutRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val result = quickBooksService.executePayout(body.bidId, claims.userId)
        return ResponseEntity.ok(result)
    }

    // ── Receipt ──

    @GetMapping("/receipt")
    fun getReceipt(
        @RequestParam bidId: UUID,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val receipt = quickBooksService.getReceipt(bidId)
        return ResponseEntity.ok(receiptToMap(receipt))
    }

    @PostMapping("/receipt")
    fun generateReceipt(
        @Valid @RequestBody body: GenerateReceiptRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val receipt = quickBooksService.generateReceipt(body.bidId, claims.userId)
        return ResponseEntity.ok(receiptToMap(receipt))
    }

    // ── Webhook ──

    @PostMapping("/webhook")
    fun intuitWebhook(
        @RequestBody payload: String,
        @RequestHeader("intuit-signature", required = false) signature: String?
    ): ResponseEntity<Any> {
        if (!quickBooksService.verifyWebhookSignature(payload, signature)) {
            log.warn("QB webhook signature verification failed")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("ok" to false))
        }
        quickBooksService.handleWebhookEvent(payload)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // Money fields (grossAmount, platformFee, totalCharged) are in cents — divide by 100 for display
    private fun receiptToMap(receipt: com.strata.ftw.domain.entity.Receipt): Map<String, Any?> = mapOf(
        "receiptId" to receipt.id,
        "receiptNumber" to receipt.receiptNumber,
        "grossAmount" to receipt.grossAmount, // cents
        "platformFee" to receipt.platformFee, // cents
        "totalCharged" to receipt.totalCharged, // cents
        "jobTitle" to receipt.jobTitle,
        "contractorName" to receipt.contractorName,
        "homeownerName" to receipt.homeownerName,
        "lineItems" to emptyList<Any>(),
        "paidAt" to receipt.paidAt,
        "createdAt" to receipt.insertedAt
    )

    private fun redirectToFrontend(status: String, detail: String?): ResponseEntity<Any> {
        val safeDetail = detail?.replace(Regex("[^a-zA-Z0-9_ -]"), "") ?: ""
        val url = "$frontendUrl/settings/integrations?qb=$status&detail=$safeDetail"
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", url)
            .build()
    }
}
