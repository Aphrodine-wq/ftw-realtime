package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.ProjectStatus
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
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
        @Valid @RequestBody req: CreateProjectRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>("name" to req.name, "budget" to req.budget)
        req.description?.let { attrs["description"] = it }
        req.contractor_id?.let { attrs["contractor_id"] = it }
        req.homeowner_id?.let { attrs["homeowner_id"] = it }
        req.job_id?.let { attrs["job_id"] = it }
        req.start_date?.let { attrs["start_date"] = it }
        req.end_date?.let { attrs["end_date"] = it }
        val project = marketplace.createProject(attrs)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("project" to marketplace.serializeProject(project)))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateProjectRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>()
        req.name?.let { attrs["name"] = it }
        req.description?.let { attrs["description"] = it }
        req.status?.let { attrs["status"] = it }
        req.budget?.let { attrs["budget"] = it }
        req.spent?.let { attrs["spent"] = it }
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
