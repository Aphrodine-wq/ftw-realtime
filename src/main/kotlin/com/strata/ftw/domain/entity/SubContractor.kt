package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sub_contractors")
class SubContractor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    var user: User? = null,

    var company: String? = null,
    var bio: String? = null,
    var specialty: String? = null,
    var skills: String? = null,  // Comma-separated
    var location: String? = null,

    @Column(name = "service_radius")
    var serviceRadius: Int = 50,

    @Column(name = "years_experience")
    var yearsExperience: Int? = null,

    @Column(name = "hourly_rate")
    var hourlyRate: Int? = null, // Amount in cents (divide by 100 for display)

    var verified: Boolean = false,
    var licensed: Boolean = false,
    var insured: Boolean = false,
    var rating: Double = 0.0,

    @Column(name = "review_count")
    var reviewCount: Int = 0,

    @Column(name = "sub_jobs_completed")
    var subJobsCompleted: Int = 0,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
