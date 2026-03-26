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
    @Value("\${app.storage.access-key:}") private val accessKey: String,
    @Value("\${app.storage.secret-key:}") private val secretKey: String,
    @Value("\${app.storage.local-path:uploads}") private val localPath: String
) {
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
        val key = "$entityType/$entityId/${UUID.randomUUID()}_${file.originalFilename}"

        if (s3Client != null) {
            s3Client!!.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.contentType)
                    .build(),
                RequestBody.fromBytes(file.bytes)
            )
            return if (endpoint.isNotBlank()) "$endpoint/$bucket/$key"
            else "https://$bucket.s3.$region.amazonaws.com/$key"
        }

        // Local storage fallback
        val dir = Path.of(localPath, entityType, entityId)
        Files.createDirectories(dir)
        val dest = dir.resolve("${UUID.randomUUID()}_${file.originalFilename}")
        file.transferTo(dest)
        return dest.toString()
    }
}
