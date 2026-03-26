package com.strata.ftw.web.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/api/health")
    fun health() = mapOf("status" to "ok", "service" to "ftw-realtime")
}
