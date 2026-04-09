package com.strata.ftw.web.controller

import com.strata.ftw.domain.entity.SubContractor
import com.strata.ftw.domain.entity.Upload
import com.strata.ftw.domain.repository.SubContractorRepository
import com.strata.ftw.domain.repository.UserRepository
import com.strata.ftw.service.FairTrustService
import com.strata.ftw.service.MarketplaceService
import com.strata.ftw.service.StorageService
import com.strata.ftw.service.TokenClaims
import com.strata.ftw.web.dto.OnboardingProfileRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/contractor")
class OnboardingController(
    private val userRepository: UserRepository,
    private val subContractorRepository: SubContractorRepository,
    private val storageService: StorageService,
    private val marketplace: MarketplaceService,
    private val fairTrust: FairTrustService
) {

    /** POST /api/contractor/onboarding — submit full onboarding profile */
    @PostMapping("/onboarding")
    fun submitProfile(
        @Valid @RequestBody req: OnboardingProfileRequest,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        // Update user profile
        val user = userRepository.findById(claims.userId).orElseThrow()
        req.license_number?.let { user.licenseNumber = it }
        req.phone?.let { user.phone = it }
        req.location?.let { user.location = it }
        userRepository.save(user)

        // Upsert SubContractor profile
        val existing = subContractorRepository.findByUserId(claims.userId)
        val profile = existing ?: SubContractor(userId = claims.userId)
        profile.company = req.company
        profile.bio = req.bio
        profile.specialty = req.specialty
        profile.skills = req.skills
        profile.location = req.location ?: user.location
        profile.serviceRadius = req.service_radius
        profile.yearsExperience = req.years_experience
        profile.hourlyRate = req.hourly_rate
        val saved = subContractorRepository.save(profile)

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("profile" to serializeProfile(saved)))
    }

    /** POST /api/contractor/licenses — upload license document */
    @PostMapping("/licenses")
    fun uploadLicense(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val path = storageService.upload(file, "license", claims.userId.toString())
        val upload = Upload(
            filename = file.originalFilename ?: "license",
            contentType = file.contentType ?: "application/octet-stream",
            size = file.size.toInt(),
            path = path,
            entityType = "license",
            entityId = claims.userId,
            uploaderId = claims.userId
        )
        val saved = marketplace.createUpload(upload)

        // Auto-submit license verification step
        fairTrust.submitVerification(claims.userId, "license", mapOf(
            "upload_id" to saved.id.toString(),
            "path" to path
        ))

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
            "upload" to mapOf(
                "id" to saved.id.toString(),
                "filename" to saved.filename,
                "path" to saved.path
            ),
            "verification_status" to "pending"
        ))
    }

    /** POST /api/contractor/insurance — upload insurance document */
    @PostMapping("/insurance")
    fun uploadInsurance(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal claims: TokenClaims
    ): ResponseEntity<Any> {
        val path = storageService.upload(file, "insurance", claims.userId.toString())
        val upload = Upload(
            filename = file.originalFilename ?: "insurance",
            contentType = file.contentType ?: "application/octet-stream",
            size = file.size.toInt(),
            path = path,
            entityType = "insurance",
            entityId = claims.userId,
            uploaderId = claims.userId
        )
        val saved = marketplace.createUpload(upload)

        // Auto-submit insurance verification step
        fairTrust.submitVerification(claims.userId, "insurance", mapOf(
            "upload_id" to saved.id.toString(),
            "path" to path
        ))

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
            "upload" to mapOf(
                "id" to saved.id.toString(),
                "filename" to saved.filename,
                "path" to saved.path
            ),
            "verification_status" to "pending"
        ))
    }

    /** GET /api/contractor/onboarding/status — full onboarding + verification status */
    @GetMapping("/onboarding/status")
    fun status(@AuthenticationPrincipal claims: TokenClaims): ResponseEntity<Any> {
        val profile = subContractorRepository.findByUserId(claims.userId)
        val verificationStatus = fairTrust.verificationStatus(claims.userId)

        return ResponseEntity.ok(mapOf(
            "profile_complete" to (profile != null),
            "profile" to profile?.let { serializeProfile(it) },
            "verification" to verificationStatus
        ))
    }

    private fun serializeProfile(p: SubContractor): Map<String, Any?> = mapOf(
        "id" to p.id.toString(),
        "company" to p.company,
        "bio" to p.bio,
        "specialty" to p.specialty,
        "skills" to p.skills,
        "location" to p.location,
        "service_radius" to p.serviceRadius,
        "years_experience" to p.yearsExperience,
        "hourly_rate" to p.hourlyRate,
        "verified" to p.verified,
        "licensed" to p.licensed,
        "insured" to p.insured,
        "rating" to p.rating,
        "review_count" to p.reviewCount,
        "created_at" to p.insertedAt?.toString()
    )
}
