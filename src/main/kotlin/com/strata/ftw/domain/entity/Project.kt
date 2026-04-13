package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "projects")
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false)
    var name: String = "",

    var description: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ProjectStatus = ProjectStatus.planning,

    @Column(name = "start_date")
    var startDate: LocalDate? = null,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    var budget: Int = 0,
    var spent: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeowner_id")
    var homeowner: User? = null,

    @Column(name = "homeowner_id", insertable = false, updatable = false)
    var homeownerId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    var job: Job? = null,

    @Column(name = "job_id", insertable = false, updatable = false)
    var jobId: UUID? = null,

    var category: String? = null,

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    var milestones: MutableList<Milestone> = mutableListOf(),

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    @OrderBy("date DESC")
    var expenses: MutableList<Expense> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class ProjectStatus {
    planning, active, on_hold, completed, cancelled
}
