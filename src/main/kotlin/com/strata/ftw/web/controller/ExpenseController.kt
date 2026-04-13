package com.strata.ftw.web.controller

import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/projects/{projectId}/expenses")
class ExpenseController(private val marketplace: MarketplaceService) {

    @GetMapping
    fun list(@PathVariable projectId: UUID): ResponseEntity<Any> {
        val expenses = marketplace.listExpenses(projectId)
        return ResponseEntity.ok(mapOf("expenses" to expenses.map { marketplace.serializeExpense(it) }))
    }

    @PostMapping
    fun create(
        @PathVariable projectId: UUID,
        @Valid @RequestBody req: CreateExpenseRequest
    ): ResponseEntity<Any> {
        val attrs = mutableMapOf<String, Any>("description" to req.description, "amount" to req.amount)
        req.category?.let { attrs["category"] = it }
        req.milestone_id?.let { attrs["milestone_id"] = it }
        req.date?.let { attrs["date"] = it }
        req.vendor?.let { attrs["vendor"] = it }
        val expense = marketplace.createExpense(projectId, attrs)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("expense" to marketplace.serializeExpense(expense)))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable projectId: UUID, @PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteExpense(id)
        return ResponseEntity.noContent().build()
    }
}
