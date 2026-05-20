package com.strata.ftw.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class StorageService(
    @Value("\${app.storage.bucket:}") private val bucket: String,
    @Value("\${app.storage.region:us-east-1}") private val region: String,
    @Value("\${app.storage.endpoint:}") private val endpoint: String,
    /** Public-facing URL base for serving uploaded files.
     *  - AWS S3: leave blank, falls back to virtual-hosted style URL
     *  - Cloudflare R2: set to your bucket's public dev URL (https://pub-xxx.r2.dev)
     *    or your custom domain (https://files.fairtradeworker.com)
     *  - The S3 API endpoint is for SigV4 uploads, not public reads. */
    @Value("\${app.storage.public-url:}") private val publicUrl: String,
    @Value("\${app.storage.access-key:}") private val accessKey: String,
    @Value("\${app.storage.secret-key:}") private val secretKey: String,
    @Value("\${app.storage.local-path:uploads}") private val localPath: String
) {
    companion object {
        // Belt-and-suspenders cap; Spring multipart also enforces 10MB at the servlet
        // boundary (application.yml). This guards the service in case a non-multipart
        // path ever calls upload() directly.
        private const val MAX_BYTES: Long = 10L * 1024 * 1024
        // Insurance docs, job photos, FairRecord certificates — that's the whole
        // universe of legitimate uploads. Anything else (HTML, JS, executables)
        // is rejected to prevent S3-served XSS and active-content delivery.
        private val ALLOWED_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif",
            "application/pdf"
        )
    }

    private val s3Client: S3Client? by lazy {
        if (bucket.isBlank() || accessKey.isBlank()) null
        else {
            val builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                ))
            if (endpoint.isNotBlank()) {
                builder.endpointOverride(URI.create(endpoint))
            }
            builder.build()
        }
    }

    fun upload(file: MultipartFile, entityType: String, entityId: String): String {
        require(!file.isEmpty) { "Upload is empty" }
        require(file.size <= MAX_BYTES) {
            "Upload exceeds ${MAX_BYTES / (1024 * 1024)}MB limit (got ${file.size} bytes)"
        }
        val contentType = file.contentType?.lowercase()
        require(contentType in ALLOWED_TYPES) {
            "Unsupported file type: $contentType. Allowed: ${ALLOWED_TYPES.joinToString(", ")}"
        }

        // Strip directory components from originalFilename — Path.getFileName() rejects
        // the "../" traversal pattern that would otherwise let an attacker escape the
        // upload prefix on local-storage fallback or pollute the S3 key namespace.
        val safeName = Path.of(file.originalFilename ?: "file").fileName.toString()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "file" }
        val key = "$entityType/$entityId/${UUID.randomUUID()}_$safeName"

        if (s3Client != null) {
            s3Client!!.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.contentType)
                    .build(),
                RequestBody.fromBytes(file.bytes)
            )
            return when {
                publicUrl.isNotBlank() -> "${publicUrl.trimEnd('/')}/$key"
                endpoint.isBlank() -> "https://$bucket.s3.$region.amazonaws.com/$key"
                else -> {
                    // Custom endpoint without explicit public URL — best-effort,
                    // but the caller should set app.storage.public-url for R2.
                    "${endpoint.trimEnd('/')}/$bucket/$key"
                }
            }
        }

        // Local storage fallback
        val dir = Path.of(localPath, entityType, entityId)
        Files.createDirectories(dir)
        val dest = dir.resolve("${UUID.randomUUID()}_$safeName")
        file.transferTo(dest)
        return dest.toString()
    }
}
