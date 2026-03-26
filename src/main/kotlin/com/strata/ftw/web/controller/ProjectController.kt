package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.ProjectStatus
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/projects")
class ProjectController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(
        @RequestParam status: String?,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val projectStatus = status?.let { ProjectStatus.valueOf(it) }
        val projects = marketplace.listProjects(claims.userId, projectStatus)
        return ResponseEntity.ok(mapOf("projects" to projects.map { marketplace.serializeProject(it) }))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<Any> {
        val project = marketplace.getProject(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("project" to marketplace.serializeProject(project)))
    }

    @PostMapping
    fun create(
        @RequestBody body: Map<String, Any>,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["project"] as? Map<String, Any> ?: body
        val project = marketplace.createProject(attrs)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("project" to marketplace.serializeProject(project)))
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        @Suppress("UNCHECKED_CAST")
        val attrs = body["project"] as? Map<String, Any> ?: body
        val project = marketplace.updateProject(id, attrs)
        return ResponseEntity.ok(mapOf("project" to marketplace.serializeProject(project)))
    }

    @GetMapping("/{projectId}/record")
    fun getProjectRecord(@PathVariable projectId: UUID): ResponseEntity<Any> {
        val record = marketplace.getFairRecordByProject(projectId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("record" to marketplace.serializeFairRecord(record)))
    }
}
