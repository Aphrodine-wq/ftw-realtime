package com.strata.ftw.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class WebhookCreateDto(
    val name: String,
    val url: String,
    val secret: String,
    @JsonProperty("event_types") val eventTypes: List<String>? = null
)

data class WebhookSummaryDto(
    val id: String,
    val name: String,
    val url: String,
    @JsonProperty("event_types") val eventTypes: List<String>,
    val active: Boolean,
    @JsonProperty("last_delivery_at") val lastDeliveryAt: String?,
    @JsonProperty("last_delivery_status") val lastDeliveryStatus: Int?,
    @JsonProperty("consecutive_failures") val consecutiveFailures: Int,
    @JsonProperty("inserted_at") val insertedAt: String?
)
