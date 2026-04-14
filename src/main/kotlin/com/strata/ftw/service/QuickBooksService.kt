package com.strata.ftw.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.domain.entity.*
import com.strata.ftw.domain.repository.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class QuickBooksService(
    private val qbCredentialRepository: QbCredentialRepository,
    private val invoiceRepository: InvoiceRepository,
    private val bidRepository: BidRepository,
    private val payoutRepository: PayoutRepository,
    private val receiptRepository: ReceiptRepository,
    private val userRepository: UserRepository,
    private val jobRepository: JobRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.quickbooks.client-id}") private val clientId: String,
    @Value("\${app.quickbooks.client-secret}") private val clientSecret: String,
    @Value("\${app.quickbooks.redirect-uri}") private val redirectUri: String,
    @Value("\${app.quickbooks.base-url}") private val baseUrl: String,
    @Value("\${app.quickbooks.token-url}") private val tokenUrl: String,
    @Value("\${app.quickbooks.webhook-verifier-token}") private val webhookVerifierToken: String,
    @Value("\${app.quickbooks.auth-url}") private val authUrl: String
) {
    private val log = LoggerFactory.getLogger(QuickBooksService::class.java)
    private val restClient = RestClient.create()

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 500L
    }

    // ── OAuth ──

    fun buildAuthUrl(state: String): String {
        val scopes = "com.intuit.quickbooks.accounting"
        return "$authUrl?client_id=${URLEncoder.encode(clientId, Charsets.UTF_8)}" +
            "&redirect_uri=${URLEncoder.encode(redirectUri, Charsets.UTF_8)}" +
            "&response_type=code" +
            "&scope=${URLEncoder.encode(scopes, Charsets.UTF_8)}" +
            "&state=${URLEncoder.encode(state, Charsets.UTF_8)}"
    }

    @Transactional
    fun exchangeCodeForTokens(code: String, realmId: String, userId: UUID): QbCredential {
        val tokenResponse = requestTokens(
            "authorization_code",
            mapOf("code" to code, "redirect_uri" to redirectUri)
        )

        val accessToken = tokenResponse.get("access_token").asText()
        val refreshToken = tokenResponse.get("refresh_token").asText()
        val expiresIn = tokenResponse.get("expires_in").asLong()

        val companyName = fetchCompanyName(realmId, accessToken)

        // Upsert credentials — one QB connection per contractor
        val existing = qbCredentialRepository.findByUserId(userId)
        val credential = existing ?: QbCredential(userId = userId)
        credential.realmId = realmId
        credential.accessToken = accessToken
        credential.refreshToken = refreshToken
        credential.tokenExpiresAt = Instant.now().plusSeconds(expiresIn)
        credential.companyName = companyName

        return qbCredentialRepository.save(credential)
    }

    @Transactional
    fun refreshAccessToken(credential: QbCredential): QbCredential {
        try {
            val tokenResponse = requestTokens(
                "refresh_token",
                mapOf("refresh_token" to credential.refreshToken)
            )

            credential.accessToken = tokenResponse.get("access_token").asText()
            credential.refreshToken = tokenResponse.get("refresh_token").asText()
            credential.tokenExpiresAt = Instant.now().plusSeconds(
                tokenResponse.get("expires_in").asLong()
            )

            return qbCredentialRepository.save(credential)
        } catch (e: QuickBooksException) {
            throw QuickBooksTokenException(
                "Token refresh failed for user ${credential.userId}. Re-authorize QuickBooks.",
                e.fault
            )
        }
    }

    fun getCredentials(userId: UUID): QbCredential? =
        qbCredentialRepository.findByUserId(userId)

    fun isConnected(userId: UUID): Boolean =
        qbCredentialRepository.findByUserId(userId) != null

    @Transactional
    fun disconnect(userId: UUID) {
        val credential = qbCredentialRepository.findByUserId(userId) ?: return
        try {
            restClient.post()
                .uri("https://developer.api.intuit.com/v2/oauth2/tokens/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Basic ${basicAuth()}")
                .body(mapOf("token" to credential.refreshToken))
                .retrieve()
                .body(String::class.java)
        } catch (e: Exception) {
            log.warn("Failed to revoke QB token for user {}: {}", userId, e.message)
        }
        qbCredentialRepository.delete(credential)
    }

    // ── Invoice Sync ──

    @Transactional
    fun createQbInvoice(invoiceId: UUID, userId: UUID): Invoice {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        // Ownership check
        if (invoice.contractorId != userId) {
            throw IllegalArgumentException("Invoice does not belong to this user")
        }

        // Idempotency — already synced, return as-is
        if (invoice.qbInvoiceId != null) {
            log.info("Invoice {} already synced to QB as {}", invoiceId, invoice.qbInvoiceId)
            return invoice
        }

        val credential = getValidCredential(userId)
        val qbInvoice = buildQbInvoicePayload(invoice)

        val response = qbApiPost(
            credential,
            "/v3/company/${credential.realmId}/invoice",
            qbInvoice
        )

        val qbInvoiceId = response.get("Invoice").get("Id").asText()
        invoice.qbInvoiceId = qbInvoiceId
        invoice.qbSyncedAt = Instant.now()
        if (invoice.status == InvoiceStatus.draft) {
            invoice.status = InvoiceStatus.sent
        }

        return invoiceRepository.save(invoice)
    }

    @Transactional
    fun recordPayment(invoiceId: UUID, userId: UUID, paymentAmount: Int? = null): Invoice {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        if (invoice.contractorId != userId) {
            throw IllegalArgumentException("Invoice does not belong to this user")
        }

        val qbInvoiceId = invoice.qbInvoiceId
            ?: throw IllegalStateException("Invoice not synced to QuickBooks yet")

        if (invoice.status == InvoiceStatus.paid) {
            throw IllegalStateException("Invoice is already paid")
        }

        val credential = getValidCredential(userId)
        val amount = paymentAmount ?: invoice.amount
        val amountDecimal = amount / 100.0

        val paymentPayload = objectMapper.createObjectNode().apply {
            put("TotalAmt", amountDecimal)
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                put("value", getQbCustomerRef(credential, qbInvoiceId))
            })
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", amountDecimal)
                    set<JsonNode>("LinkedTxn", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().apply {
                            put("TxnId", qbInvoiceId)
                            put("TxnType", "Invoice")
                        }
                    ))
                }
            ))
        }

        qbApiPost(credential, "/v3/company/${credential.realmId}/payment", paymentPayload)

        invoice.status = InvoiceStatus.paid
        invoice.paidAt = Instant.now()

        return invoiceRepository.save(invoice)
    }

    fun getQbInvoice(invoiceId: UUID, userId: UUID): JsonNode {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { IllegalArgumentException("Invoice not found: $invoiceId") }

        if (invoice.contractorId != userId) {
            throw IllegalArgumentException("Invoice does not belong to this user")
        }

        val qbInvoiceId = invoice.qbInvoiceId
            ?: throw IllegalStateException("Invoice not synced to QuickBooks yet")

        val credential = getValidCredential(userId)
        return qbApiGet(credential, "/v3/company/${credential.realmId}/invoice/$qbInvoiceId")
    }

    // ── Create Invoice from Bid ──

    @Transactional
    fun createInvoiceFromBid(
        bidId: UUID,
        userId: UUID,
        milestone: String?,
        dueDate: String?,
        customerName: String,
        customerEmail: String?,
        description: String?
    ): Map<String, Any?> {
        val bid = bidRepository.findById(bidId)
            .orElseThrow { IllegalArgumentException("Bid not found: $bidId") }

        if (bid.contractorId != userId) {
            throw IllegalArgumentException("Bid does not belong to this user")
        }

        val credential = getValidCredential(userId)
        val amountDecimal = bid.amount / 100.0

        val lineDescription = description
            ?: milestone?.let { "Milestone: $it" }
            ?: "FairTradeWorker — Bid ${bidId.toString().take(8)}"

        val invoicePayload = objectMapper.createObjectNode().apply {
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                put("value", "1") // TODO: look up or create QB customer by name/email
                put("name", customerName)
            })
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", amountDecimal)
                    put("DetailType", "SalesItemLineDetail")
                    put("Description", lineDescription)
                    set<JsonNode>("SalesItemLineDetail", objectMapper.createObjectNode().apply {
                        set<JsonNode>("ItemRef", objectMapper.createObjectNode().apply {
                            put("value", "1")
                            put("name", "Services")
                        })
                        put("Qty", 1)
                        put("UnitPrice", amountDecimal)
                    })
                }
            ))
            dueDate?.let { put("DueDate", it) }
            customerEmail?.let {
                set<JsonNode>("BillEmail", objectMapper.createObjectNode().apply {
                    put("Address", it)
                })
            }
        }

        val response = qbApiPost(credential, "/v3/company/${credential.realmId}/invoice", invoicePayload)
        val qbInvoiceId = response.get("Invoice").get("Id").asText()
        val docNumber = response.get("Invoice").get("DocNumber").asText()

        log.info("Created QB invoice for bid {} — invoiceId={}, docNumber={}", bidId, qbInvoiceId, docNumber)

        return mapOf(
            "created" to true,
            "qbInvoiceId" to qbInvoiceId,
            "qbDocNumber" to docNumber,
            "invoiceLink" to "https://app.sandbox.qbo.intuit.com/app/invoice?txnId=$qbInvoiceId"
        )
    }

    // ── Sync Estimate to QB ──

    @Transactional
    fun syncEstimate(
        userId: UUID,
        customerName: String,
        customerEmail: String?,
        lineItems: List<Map<String, Any>>,
        expirationDate: String?,
        title: String?
    ): Map<String, Any?> {
        val credential = getValidCredential(userId)

        val estimatePayload = objectMapper.createObjectNode().apply {
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                put("value", "1") // TODO: look up or create QB customer
                put("name", customerName)
            })

            val lines = objectMapper.createArrayNode()
            for (item in lineItems) {
                val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                val unitPrice = (item["unitPrice"] as? Number)?.toInt() ?: 0
                val desc = item["description"] as? String ?: ""
                val unitPriceDecimal = unitPrice / 100.0

                lines.add(objectMapper.createObjectNode().apply {
                    put("Amount", unitPriceDecimal * qty)
                    put("DetailType", "SalesItemLineDetail")
                    put("Description", desc)
                    set<JsonNode>("SalesItemLineDetail", objectMapper.createObjectNode().apply {
                        set<JsonNode>("ItemRef", objectMapper.createObjectNode().apply {
                            put("value", "1")
                            put("name", "Services")
                        })
                        put("Qty", qty)
                        put("UnitPrice", unitPriceDecimal)
                    })
                })
            }
            set<JsonNode>("Line", lines)

            expirationDate?.let { put("ExpirationDate", it) }
            customerEmail?.let {
                set<JsonNode>("BillEmail", objectMapper.createObjectNode().apply {
                    put("Address", it)
                })
            }
            title?.let { put("PrivateNote", it) }
        }

        val response = qbApiPost(credential, "/v3/company/${credential.realmId}/estimate", estimatePayload)
        val qbEstimateId = response.get("Estimate").get("Id").asText()
        val docNumber = response.get("Estimate").get("DocNumber").asText()

        log.info("Synced estimate to QB for user {} — estimateId={}", userId, qbEstimateId)

        return mapOf(
            "synced" to true,
            "qbEstimateId" to qbEstimateId,
            "qbDocNumber" to docNumber
        )
    }

    // ── Payout ──

    fun getPayout(bidId: UUID): Payout {
        return payoutRepository.findByBidId(bidId)
            ?: throw IllegalArgumentException("No payout found for bid: $bidId")
    }

    @Transactional
    fun executePayout(bidId: UUID, userId: UUID): Map<String, Any?> {
        val bid = bidRepository.findById(bidId)
            .orElseThrow { IllegalArgumentException("Bid not found: $bidId") }

        if (bid.contractorId != userId) {
            throw IllegalArgumentException("Bid does not belong to this user")
        }

        // Check for existing payout
        val existing = payoutRepository.findByBidId(bidId)
        if (existing != null) {
            throw IllegalStateException("Payout already exists for bid: $bidId")
        }

        val credential = getValidCredential(userId)
        val grossAmount = bid.amount
        val feePercent = 5.0
        val platformFee = (grossAmount * feePercent / 100.0).toInt()
        val netAmount = grossAmount - platformFee

        val payout = Payout(
            bidId = bidId,
            grossAmount = grossAmount,
            platformFee = platformFee,
            netAmount = netAmount,
            feePercent = feePercent,
            status = PayoutStatus.PROCESSING
        )

        val netAmountDecimal = netAmount / 100.0

        // Create QB Bill (what we owe the contractor)
        val billPayload = objectMapper.createObjectNode().apply {
            set<JsonNode>("VendorRef", objectMapper.createObjectNode().apply {
                put("value", "1") // TODO: look up or create QB vendor for contractor
            })
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", netAmountDecimal)
                    put("DetailType", "AccountBasedExpenseLineDetail")
                    put("Description", "FTW Payout — Bid ${bidId.toString().take(8)}")
                    set<JsonNode>("AccountBasedExpenseLineDetail", objectMapper.createObjectNode().apply {
                        set<JsonNode>("AccountRef", objectMapper.createObjectNode().apply {
                            put("value", "1") // Expenses account
                        })
                    })
                }
            ))
            put("DueDate", java.time.LocalDate.now().toString())
        }

        val billResponse = qbApiPost(credential, "/v3/company/${credential.realmId}/bill", billPayload)
        val qbBillId = billResponse.get("Bill").get("Id").asText()

        payout.qbBillId = qbBillId
        payoutRepository.save(payout)

        // Pay the Bill (execute the transfer)
        val billPaymentPayload = objectMapper.createObjectNode().apply {
            set<JsonNode>("VendorRef", objectMapper.createObjectNode().apply {
                put("value", billResponse.get("Bill").get("VendorRef").get("value").asText())
            })
            put("PayType", "Check")
            put("TotalAmt", netAmountDecimal)
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", netAmountDecimal)
                    set<JsonNode>("LinkedTxn", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().apply {
                            put("TxnId", qbBillId)
                            put("TxnType", "Bill")
                        }
                    ))
                }
            ))
            set<JsonNode>("CheckPayment", objectMapper.createObjectNode().apply {
                set<JsonNode>("BankAccountRef", objectMapper.createObjectNode().apply {
                    put("value", "1") // Primary checking account
                })
            })
        }

        val billPaymentResponse = qbApiPost(credential, "/v3/company/${credential.realmId}/billpayment", billPaymentPayload)
        val qbBillPaymentId = billPaymentResponse.get("BillPayment").get("Id").asText()

        payout.qbBillPaymentId = qbBillPaymentId
        payout.status = PayoutStatus.COMPLETED
        payout.paidAt = Instant.now()

        val saved = payoutRepository.save(payout)

        log.info("Executed payout for bid {} — gross={} fee={} net={} billId={} paymentId={}", bidId, grossAmount, platformFee, netAmount, qbBillId, qbBillPaymentId)

        return mapOf(
            "success" to true,
            "payoutId" to saved.id,
            "grossAmount" to grossAmount,
            "platformFee" to platformFee,
            "feePercent" to feePercent,
            "netAmount" to netAmount,
            "qbBillId" to qbBillId,
            "qbBillPaymentId" to qbBillPaymentId
        )
    }

    // ── Receipt ──

    fun getReceipt(bidId: UUID): Receipt {
        return receiptRepository.findByBidId(bidId)
            ?: throw IllegalArgumentException("No receipt found for bid: $bidId")
    }

    @Transactional
    fun generateReceipt(bidId: UUID, userId: UUID): Receipt {
        // Check for existing receipt
        val existing = receiptRepository.findByBidId(bidId)
        if (existing != null) {
            return existing
        }

        val bid = bidRepository.findById(bidId)
            .orElseThrow { IllegalArgumentException("Bid not found: $bidId") }

        val job = bid.job ?: bid.jobId?.let { jobRepository.findById(it).orElse(null) }
            ?: throw IllegalStateException("Job not found for bid: $bidId")

        val contractor = bid.contractor ?: bid.contractorId?.let { userRepository.findById(it).orElse(null) }
        val homeowner = job.homeowner ?: job.homeownerId?.let { userRepository.findById(it).orElse(null) }

        val grossAmount = bid.amount
        val homeownerFeePercent = 3.0
        val platformFee = (grossAmount * homeownerFeePercent / 100.0).toInt()
        val totalCharged = grossAmount + platformFee

        val receiptNumber = "RCP-${System.currentTimeMillis().toString().takeLast(8)}"

        val receipt = Receipt(
            receiptNumber = receiptNumber,
            bidId = bidId,
            grossAmount = grossAmount,
            platformFee = platformFee,
            totalCharged = totalCharged,
            jobTitle = job.title,
            contractorName = contractor?.name ?: "Unknown",
            homeownerName = homeowner?.name ?: "Unknown",
            paidAt = Instant.now()
        )

        val saved = receiptRepository.save(receipt)
        log.info("Generated receipt {} for bid {}", receiptNumber, bidId)
        return saved
    }

    // ── Webhook ──

    fun verifyWebhookSignature(payload: String, signature: String?): Boolean {
        if (webhookVerifierToken.isBlank() || signature.isNullOrBlank()) {
            log.warn("Webhook verification skipped — missing token or signature")
            return false
        }
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(webhookVerifierToken.toByteArray(), "HmacSHA256"))
            val computed = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
            computed == signature
        } catch (e: Exception) {
            log.error("Webhook signature verification failed: {}", e.message)
            false
        }
    }

    @Transactional
    fun handleWebhookEvent(payload: String) {
        val node = objectMapper.readTree(payload)
        val eventNotifications = node.path("eventNotifications")
        if (!eventNotifications.isArray) return

        for (notification in eventNotifications) {
            val entities = notification.path("dataChangeEvent").path("entities")
            if (!entities.isArray) continue

            for (entity in entities) {
                val entityName = entity.path("name").asText()
                val operation = entity.path("operation").asText()
                val entityId = entity.path("id").asText()

                log.info("QB webhook: {} {} {}", operation, entityName, entityId)

                if (entityName == "Payment" && operation == "Create") {
                    // TODO: look up which invoice this payment applies to,
                    // update internal status, and queue contractor payout
                    log.info("QB payment received — entity {}, queuing payout processing", entityId)
                }
            }
        }
    }

    // ── Private helpers ──

    private fun getValidCredential(userId: UUID): QbCredential {
        val credential = qbCredentialRepository.findByUserId(userId)
            ?: throw IllegalStateException("QuickBooks not connected. Complete OAuth first.")

        // Refresh if token expires within 5 minutes
        return if (credential.tokenExpiresAt.isBefore(Instant.now().plusSeconds(300))) {
            refreshAccessToken(credential)
        } else {
            credential
        }
    }

    private fun requestTokens(grantType: String, params: Map<String, String>): JsonNode {
        val formData = mutableMapOf("grant_type" to grantType)
        formData.putAll(params)
        val formBody = formData.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
        }

        val response = restClient.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Authorization", "Basic ${basicAuth()}")
            .header("Accept", "application/json")
            .body(formBody)
            .retrieve()
            .body(String::class.java)
            ?: throw QuickBooksException("Empty response from token endpoint")

        val node = objectMapper.readTree(response)
        if (node.has("error")) {
            throw QuickBooksException("QB OAuth error: ${node.get("error").asText()} - ${node.path("error_description").asText()}")
        }
        return node
    }

    private fun fetchCompanyName(realmId: String, accessToken: String): String? {
        return try {
            val response = restClient.get()
                .uri("$baseUrl/v3/company/$realmId/companyinfo/$realmId?minorversion=73")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .retrieve()
                .body(String::class.java)

            val node = objectMapper.readTree(response)
            node.path("CompanyInfo").path("CompanyName").asText(null)
        } catch (e: Exception) {
            log.warn("Could not fetch QB company name: {}", e.message)
            null
        }
    }

    private fun buildQbInvoicePayload(invoice: Invoice): JsonNode {
        val amountDecimal = invoice.amount / 100.0

        return objectMapper.createObjectNode().apply {
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                put("value", "1") // Sandbox default customer
            })
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", amountDecimal)
                    put("DetailType", "SalesItemLineDetail")
                    put("Description", buildInvoiceDescription(invoice))
                    set<JsonNode>("SalesItemLineDetail", objectMapper.createObjectNode().apply {
                        set<JsonNode>("ItemRef", objectMapper.createObjectNode().apply {
                            put("value", "1")
                            put("name", "Services")
                        })
                        put("Qty", 1)
                        put("UnitPrice", amountDecimal)
                    })
                }
            ))
            invoice.dueDate?.let { put("DueDate", it.toString()) }
            put("DocNumber", invoice.invoiceNumber)
            put("PrivateNote", "FTW Invoice ${invoice.invoiceNumber} | ID: ${invoice.id}")
        }
    }

    private fun buildInvoiceDescription(invoice: Invoice): String {
        val parts = mutableListOf("FairTradeWorker Invoice ${invoice.invoiceNumber}")
        invoice.notes?.let { parts.add(it) }
        return parts.joinToString(" — ")
    }

    private fun getQbCustomerRef(credential: QbCredential, qbInvoiceId: String): String {
        return try {
            val invoiceData = qbApiGet(
                credential,
                "/v3/company/${credential.realmId}/invoice/$qbInvoiceId"
            )
            invoiceData.path("Invoice").path("CustomerRef").path("value").asText("1")
        } catch (e: Exception) {
            log.warn("Could not fetch QB customer ref, using default: {}", e.message)
            "1"
        }
    }

    /**
     * POST to QB API with retry on transient failures (network errors, 5xx).
     * QuickBooksException (QB returned a Fault) is NOT retried — that's a business error.
     */
    private fun qbApiPost(credential: QbCredential, path: String, body: Any): JsonNode {
        return withRetry("POST $path") {
            val response = restClient.post()
                .uri("$baseUrl$path?minorversion=73")
                .header("Authorization", "Bearer ${credential.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "application/json")
                .body(body)
                .retrieve()
                .body(String::class.java)
                ?: throw QuickBooksException("Empty response from QB API: POST $path")

            parseQbResponse(response, "POST $path")
        }
    }

    private fun qbApiGet(credential: QbCredential, path: String): JsonNode {
        return withRetry("GET $path") {
            val response = restClient.get()
                .uri("$baseUrl$path?minorversion=73")
                .header("Authorization", "Bearer ${credential.accessToken}")
                .header("Accept", "application/json")
                .retrieve()
                .body(String::class.java)
                ?: throw QuickBooksException("Empty response from QB API: GET $path")

            parseQbResponse(response, "GET $path")
        }
    }

    private fun parseQbResponse(response: String, context: String): JsonNode {
        val node = objectMapper.readTree(response)
        if (node.has("Fault")) {
            val fault = node.get("Fault")
            val errors = fault.path("Error")
            val message = if (errors.isArray && errors.size() > 0) {
                errors[0].path("Message").asText("Unknown error")
            } else {
                "Unknown QB API error"
            }
            throw QuickBooksException("QB API error on $context: $message", fault)
        }
        return node
    }

    /**
     * Retry transient failures (network, timeouts) up to MAX_RETRIES times.
     * QuickBooksException is NOT retried — it means QB processed the request and returned an error.
     */
    private fun <T> withRetry(operation: String, block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: QuickBooksException) {
                // Business error from QB — don't retry
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    log.warn("QB API call {} failed (attempt {}), retrying: {}", operation, attempt + 1, e.message)
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw QuickBooksException("QB API call $operation failed after ${MAX_RETRIES + 1} attempts: ${lastException?.message}")
    }

    private fun basicAuth(): String =
        Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
}

open class QuickBooksException(
    message: String,
    val fault: JsonNode? = null
) : RuntimeException(message)

class QuickBooksTokenException(
    message: String,
    fault: JsonNode? = null
) : QuickBooksException(message, fault)
