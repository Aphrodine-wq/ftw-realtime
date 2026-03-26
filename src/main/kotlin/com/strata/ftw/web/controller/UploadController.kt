package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.Upload
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.StorageService
import com.strata.ftw.service.TokenClaims
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val marketplace: MarketplaceService,
    private val storageService: StorageService
) {

    @PostMapping
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("entity_type") entityType: String,
        @RequestParam("entity_id") entityId: String,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val path = storageService.upload(file, entityType, entityId)
        val upload = Upload(
            filename = file.originalFilename ?: "unknown",
            contentType = file.contentType ?: "application/octet-stream",
            size = file.size.toInt(),
            path = path,
            entityType = entityType,
            entityId = UUID.fromString(entityId),
            uploaderId = claims.userId
        )
        val saved = marketplace.createUpload(upload)
        return ResponseEntity.ok(mapOf(
            "upload" to mapOf(
                "id" to saved.id.toString(),
                "filename" to saved.filename,
                "content_type" to saved.contentType,
                "size" to saved.size,
                "path" to saved.path,
                "created_at" to saved.insertedAt?.toString()
            )
        ))
    }

    @GetMapping
    fun list(
        @RequestParam("entity_type") entityType: String,
        @RequestParam("entity_id") entityId: String
    ): ResponseEntity<Any> {
        val uploads = marketplace.listUploads(entityType, UUID.fromString(entityId))
        return ResponseEntity.ok(mapOf("uploads" to uploads.map {
            mapOf(
                "id" to it.id.toString(),
                "filename" to it.filename,
                "content_type" to it.contentType,
                "size" to it.size,
                "path" to it.path,
                "created_at" to it.insertedAt?.toString()
            )
        }))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Any> {
        marketplace.deleteUpload(id)
        return ResponseEntity.noContent().build()
    }
}
