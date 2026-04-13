package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/projects/{projectId}/milestones")
class MilestoneController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@PathVariable projectId: UUID): ResponseEntity<Any> {
        val milestones = marketplace.listMilestones(projectId)
        return ResponseEntity.ok(mapOf("milestones" to milestones.map { marketplace.serializeMilestone(it) }))
    }

    @PostMapping
    fun create(
        @PathVariable projectId: UUID,
        @Valid @RequestBody req: CreateMilestoneRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>("title" to req.title, "amount" to req.amount, "sort_order" to req.sort_order)
        req.description?.let { attrs["description"] = it }
        req.due_date?.let { attrs["due_date"] = it }
        val milestone = marketplace.createMilestone(projectId, attrs)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("milestone" to marketplace.serializeMilestone(milestone)))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable projectId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateMilestoneRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>()
        req.title?.let { attrs["title"] = it }
        req.description?.let { attrs["description"] = it }
        req.amount?.let { attrs["amount"] = it }
        req.status?.let { attrs["status"] = it }
        req.due_date?.let { attrs["due_date"] = it }
        req.note?.let { attrs["note"] = it }
        req.sort_order?.let { attrs["sort_order"] = it }
        val milestone = marketplace.updateMilestone(id, attrs)
        return ResponseEntity.ok(mapOf("milestone" to marketplace.serializeMilestone(milestone)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable projectId: UUID, @PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteMilestone(id)
        return ResponseEntity.noContent().build()
    }
}
