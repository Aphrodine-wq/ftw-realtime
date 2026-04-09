package com.strata.ftw.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.UUID

// ── Auth ──

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    val name: String,

    @field:NotBlank(message = "Role is required")
    val role: String,

    val location: String? = null
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String
)

data class SwitchRoleRequest(
    @field:NotBlank(message = "Role is required")
    val role: String
)

// ── Jobs ──

data class CreateJobRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 500, message = "Title must be under 500 characters")
    val title: String,

    val description: String? = null,
    val category: String? = null,

    @field:Min(0, message = "Budget min must be non-negative")
    val budget_min: Int? = null,

    @field:Min(0, message = "Budget max must be non-negative")
    val budget_max: Int? = null,

    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class PlaceBidRequest(
    @field:NotNull(message = "Amount is required")
    @field:Min(1, message = "Amount must be positive")
    val amount: Int,

    val message: String? = null,
    val timeline: String? = null
)

data class TransitionJobRequest(
    @field:NotBlank(message = "Status is required")
    val status: String
)

// ── Chat ──

data class SendMessageRequest(
    @field:NotBlank(message = "Message body is required")
    val body: String
)

// ── Estimates ──

data class CreateEstimateRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    val description: String? = null,

    @field:Min(0, message = "Total must be non-negative")
    val total: Int = 0,

    val notes: String? = null,
    val client_id: String? = null,
    val job_id: String? = null,

    @field:Valid
    val line_items: List<LineItemRequest> = emptyList()
)

data class LineItemRequest(
    val description: String? = null,

    @field:Min(0, message = "Quantity must be non-negative")
    val quantity: Double = 1.0,

    val unit: String? = null,

    @field:Min(0, message = "Unit price must be non-negative")
    val unit_price: Int = 0,

    @field:Min(0, message = "Total must be non-negative")
    val total: Int = 0,

    val category: String? = null
)

data class UpdateEstimateRequest(
    val title: String? = null,
    val description: String? = null,
    val total: Int? = null,
    val notes: String? = null,
    val status: String? = null
)

// ── Invoices ──

data class CreateInvoiceRequest(
    @field:NotBlank(message = "Invoice number is required")
    val invoice_number: String,

    @field:NotNull(message = "Amount is required")
    @field:Min(1, message = "Amount must be positive")
    val amount: Int,

    val notes: String? = null,
    val due_date: String? = null,
    val client_id: String? = null,
    val estimate_id: String? = null,
    val project_id: String? = null
)

data class UpdateInvoiceRequest(
    val amount: Int? = null,
    val status: String? = null,
    val notes: String? = null,
    val due_date: String? = null
)

// ── Projects ──

data class CreateProjectRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val description: String? = null,

    @field:Min(0, message = "Budget must be non-negative")
    val budget: Int = 0,

    val contractor_id: String? = null,
    val homeowner_id: String? = null,
    val job_id: String? = null,
    val start_date: String? = null,
    val end_date: String? = null
)

data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
    val status: String? = null,
    val budget: Int? = null,
    val spent: Int? = null
)

// ── Clients ──

data class CreateClientRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:Email(message = "Must be a valid email")
    val email: String? = null,

    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null
)

data class UpdateClientRequest(
    val name: String? = null,
    @field:Email(message = "Must be a valid email")
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null
)

// ── Reviews ──

data class CreateReviewRequest(
    @field:NotNull(message = "Rating is required")
    @field:Min(1, message = "Rating must be between 1 and 5")
    @field:Max(5, message = "Rating must be between 1 and 5")
    val rating: Int,

    val comment: String? = null,

    @field:NotNull(message = "Contractor ID is required")
    val reviewed_id: UUID,

    val job_id: UUID? = null
)

data class RespondToReviewRequest(
    @field:NotBlank(message = "Response is required")
    val response: String
)

// ── Sub-Jobs ──

data class CreateSubJobRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    val project_id: String? = null,
    val milestone_label: String? = null,
    val milestone_index: Int = 0,
    val description: String? = null,
    val category: String? = null,
    val skills: String? = null,
    val location: String? = null,

    @field:Min(0, message = "Budget min must be non-negative")
    val budget_min: Int? = null,

    @field:Min(0, message = "Budget max must be non-negative")
    val budget_max: Int? = null,

    val payment_path: String = "contractor_escrow",
    val disclosed_to_owner: Boolean = false,
    val deadline: String? = null
)

data class PlaceSubBidRequest(
    @field:NotNull(message = "Amount is required")
    @field:Min(1, message = "Amount must be positive")
    val amount: Int,

    val message: String? = null,
    val timeline: String? = null
)

// ── Settings ──

data class UpdateSettingsRequest(
    val notifications_email: Boolean? = null,
    val notifications_push: Boolean? = null,
    val notifications_sms: Boolean? = null,
    val appearance_theme: String? = null,
    val language: String? = null,
    val timezone: String? = null,
    val privacy_profile_visible: Boolean? = null,
    val privacy_show_rating: Boolean? = null
)

// ── Push ──

data class RegisterPushTokenRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,

    @field:NotBlank(message = "Platform is required")
    val platform: String
)

data class UnregisterPushTokenRequest(
    @field:NotBlank(message = "Token is required")
    val token: String
)

// ── Verification ──

data class SubmitVerificationRequest(
    val data: Map<String, Any> = emptyMap()
)

// ── Onboarding ──

data class OnboardingProfileRequest(
    @field:NotBlank(message = "Company name is required")
    val company: String,

    val bio: String? = null,
    val specialty: String? = null,
    val skills: String? = null,
    val location: String? = null,

    @field:Min(0, message = "Service radius must be non-negative")
    val service_radius: Int = 50,

    @field:Min(0, message = "Years of experience must be non-negative")
    val years_experience: Int? = null,

    @field:Min(0, message = "Hourly rate must be non-negative")
    val hourly_rate: Double? = null,

    val license_number: String? = null,
    val phone: String? = null
)

// ── Admin ──

data class UpdateUserStatusRequest(
    @field:NotBlank(message = "Status is required")
    val status: String  // "active" or "suspended"
)

data class ResolveDisputeRequest(
    @field:NotBlank(message = "Resolution notes are required")
    val resolution_notes: String,

    @field:NotBlank(message = "Resolution is required")
    val resolution: String  // "resolved", "dismissed", "escalated"
)
