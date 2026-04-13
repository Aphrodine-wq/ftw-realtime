package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "expenses")
class Expense(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @Column(name = "project_id", insertable = false, updatable = false)
    var projectId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    var milestone: Milestone? = null,

    @Column(name = "milestone_id", insertable = false, updatable = false)
    var milestoneId: UUID? = null,

    @Column(nullable = false)
    var description: String = "",

    var amount: Int = 0,

    var category: String? = null,

    @Column(nullable = false)
    var date: LocalDate = LocalDate.now(),

    var vendor: String? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
