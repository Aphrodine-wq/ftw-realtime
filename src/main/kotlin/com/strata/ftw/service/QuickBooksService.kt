package com.strata.ftw.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.strata.ftw.domain.entity.Invoice
import com.strata.ftw.domain.entity.InvoiceStatus
import com.strata.ftw.domain.entity.QbCredential
import com.strata.ftw.domain.repository.InvoiceRepository
import com.strata.ftw.domain.repository.QbCredentialRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class QuickBooksService(
    private val qbCredentialRepository: QbCredentialRepository,
    private val invoiceRepository: InvoiceRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.quickbooks.client-id}") private val clientId: String,
    @Value("\${app.quickbooks.client-secret}") private val clientSecret: String,
    @Value("\${app.quickbooks.redirect-uri}") private val redirectUri: String,
    @Value("\${app.quickbooks.base-url}") private val baseUrl: String,
    @Value("\${app.quickbooks.token-url}") private val tokenUrl: String
) {
    private val log = LoggerFactory.getLogger(QuickBooksService::class.java)
    private val restClient = RestClient.create()

    // ── OAuth ──

    @Transactional
    fun exchangeCodeForTokens(code: String, realmId: String, userId: UUID): QbCredential {
        val tokenResponse = requestTokens(
            "authorization_code",
            mapOf("code" to code, "redirect_uri" to redirectUri)
        )

        val accessToken = tokenResponse.get("access_token").asText()
        val refreshToken = tokenResponse.get("refresh_token").asText()
        val expiresIn = tokenResponse.get("expires_in").asLong()

        // Fetch company name from QB
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
    }

    fun getCredentials(userId: UUID): QbCredential? =
        qbCredentialRepository.findByUserId(userId)

    fun isConnected(userId: UUID): Boolean =
        qbCredentialRepository.findByUserId(userId) != null

    @Transactional
    fun disconnect(userId: UUID) {
        val credential = qbCredentialRepository.findByUserId(userId) ?: return
        // Revoke token at Intuit
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
        val credential = getValidCredential(userId)

        // Build QB invoice payload
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
        val credential = getValidCredential(userId)

        val qbInvoiceId = invoice.qbInvoiceId
            ?: throw IllegalStateException("Invoice not synced to QuickBooks yet")

        val amount = paymentAmount ?: invoice.amount
        val amountDecimal = amount / 100.0

        // Create a QB Payment linked to the invoice
        val paymentPayload = objectMapper.createObjectNode().apply {
            put("TotalAmt", amountDecimal)
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                // Use the customer from the QB invoice
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
        val credential = getValidCredential(userId)
        val qbInvoiceId = invoice.qbInvoiceId
            ?: throw IllegalStateException("Invoice not synced to QuickBooks yet")

        return qbApiGet(credential, "/v3/company/${credential.realmId}/invoice/$qbInvoiceId")
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
        val formBody = formData.entries.joinToString("&") { "${it.key}=${it.value}" }

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
            // Customer — use client email or name to find/create in QB
            set<JsonNode>("CustomerRef", objectMapper.createObjectNode().apply {
                // For sandbox, we create a minimal customer reference
                // In prod, this would look up or create the QB customer
                put("value", "1") // Sandbox default customer
            })
            set<JsonNode>("Line", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().apply {
                    put("Amount", amountDecimal)
                    put("DetailType", "SalesItemLineDetail")
                    put("Description", buildInvoiceDescription(invoice))
                    set<JsonNode>("SalesItemLineDetail", objectMapper.createObjectNode().apply {
                        set<JsonNode>("ItemRef", objectMapper.createObjectNode().apply {
                            put("value", "1") // Sandbox default service item
                            put("name", "Services")
                        })
                        put("Qty", 1)
                        put("UnitPrice", amountDecimal)
                    })
                }
            ))
            invoice.dueDate?.let { put("DueDate", it.toString()) }
            put("DocNumber", invoice.invoiceNumber)

            // Private note with FTW reference
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

    private fun qbApiPost(credential: QbCredential, path: String, body: Any): JsonNode {
        val response = restClient.post()
            .uri("$baseUrl$path?minorversion=73")
            .header("Authorization", "Bearer ${credential.accessToken}")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json")
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: throw QuickBooksException("Empty response from QB API: POST $path")

        val node = objectMapper.readTree(response)
        if (node.has("Fault")) {
            val fault = node.get("Fault")
            val errors = fault.path("Error")
            val message = if (errors.isArray && errors.size() > 0) {
                errors[0].path("Message").asText("Unknown error")
            } else {
                "Unknown QB API error"
            }
            throw QuickBooksException("QB API error on POST $path: $message", fault)
        }
        return node
    }

    private fun qbApiGet(credential: QbCredential, path: String): JsonNode {
        val response = restClient.get()
            .uri("$baseUrl$path?minorversion=73")
            .header("Authorization", "Bearer ${credential.accessToken}")
            .header("Accept", "application/json")
            .retrieve()
            .body(String::class.java)
            ?: throw QuickBooksException("Empty response from QB API: GET $path")

        val node = objectMapper.readTree(response)
        if (node.has("Fault")) {
            val fault = node.get("Fault")
            val errors = fault.path("Error")
            val message = if (errors.isArray && errors.size() > 0) {
                errors[0].path("Message").asText("Unknown error")
            } else {
                "Unknown QB API error"
            }
            throw QuickBooksException("QB API error on GET $path: $message", fault)
        }
        return node
    }

    private fun basicAuth(): String =
        Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
}

class QuickBooksException(
    message: String,
    val fault: JsonNode? = null
) : RuntimeException(message)
