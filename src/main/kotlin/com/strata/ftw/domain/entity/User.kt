package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(unique = true, nullable = false)
    var email: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var role: UserRole = UserRole.homeowner,

    @Column(name = "active_role")
    @Enumerated(EnumType.STRING)
    var activeRole: UserRole = UserRole.homeowner,

    @Column(name = "roles", columnDefinition = "VARCHAR(255)")
    var roles: String = "",  // Comma-separated roles e.g. "contractor,sub_contractor"

    var phone: String? = null,
    var location: String? = null,

    @Column(name = "license_number")
    var licenseNumber: String? = null,

    @Column(name = "insurance_verified")
    var insuranceVerified: Boolean = false,

    var rating: Double = 0.0,

    @Column(name = "jobs_completed")
    var jobsCompleted: Int = 0,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    var active: Boolean = true,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    var latitude: Double? = null,
    var longitude: Double? = null,

    @Column(name = "quality_score")
    var qualityScore: Int? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @OneToMany(mappedBy = "homeowner", fetch = FetchType.LAZY)
    var postedJobs: MutableList<Job> = mutableListOf(),

    @OneToMany(mappedBy = "contractor", fetch = FetchType.LAZY)
    var bids: MutableList<Bid> = mutableListOf(),

    @OneToMany(mappedBy = "subContractor", fetch = FetchType.LAZY)
    var subBids: MutableList<SubBid> = mutableListOf()
) {
    fun getRolesList(): List<UserRole> =
        if (roles.isBlank()) listOf(role)
        else roles.split(",").map { UserRole.valueOf(it.trim()) }

    fun hasRole(r: UserRole): Boolean = getRolesList().contains(r)

    fun addRole(r: UserRole) {
        val current = getRolesList().toMutableSet()
        current.add(r)
        roles = current.joinToString(",") { it.name }
    }
}

enum class UserRole {
    homeowner, contractor, sub_contractor, admin
}
