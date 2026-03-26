package com.strata.ftw.service

import com.strata.ftw.domain.repository.PushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.UUID

@Service
class PushService(
    private val pushTokenRepository: PushTokenRepository,
    @Value("\${app.push.expo-token:}") private val expoToken: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val expoUrl = "https://exp.host/--/api/v2/push/send"

    @Async
    fun sendPush(userId: UUID, title: String, body: String, data: Map<String, Any> = emptyMap()) {
        val tokens = pushTokenRepository.findByUserId(userId)
        if (tokens.isEmpty()) return

        val messages = tokens.map { token ->
            mapOf(
                "to" to token.token,
                "title" to title,
                "body" to body,
                "data" to data,
                "sound" to "default"
            )
        }

        try {
            val headers = mutableMapOf("Content-Type" to "application/json")
            if (expoToken.isNotBlank()) {
                headers["Authorization"] = "Bearer $expoToken"
            }
            restTemplate.postForEntity(expoUrl, messages, String::class.java)
            log.info("Push sent to {} devices for user {}", tokens.size, userId)
        } catch (e: Exception) {
            log.error("Failed to send push to user {}", userId, e)
        }
    }
}
