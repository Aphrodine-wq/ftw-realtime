package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "milestones")
class Milestone(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @Column(name = "project_id", insertable = false, updatable = false)
    var projectId: UUID? = null,

    @Column(nullable = false)
    var title: String = "",

    var description: String? = null,

    var amount: Int = 0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: MilestoneStatus = MilestoneStatus.pending,

    @Column(name = "sort_order")
    var sortOrder: Int = 0,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(name = "completed_date")
    var completedDate: LocalDate? = null,

    @Column(name = "paid_date")
    var paidDate: LocalDate? = null,

    var note: String? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class MilestoneStatus {
    pending, in_progress, complete, paid, delayed
}
