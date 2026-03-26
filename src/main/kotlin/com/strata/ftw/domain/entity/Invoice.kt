package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "invoices")
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "invoice_number", nullable = false)
    var invoiceNumber: String = "",

    @Column(nullable = false)
    var amount: Int = 0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus = InvoiceStatus.draft,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id")
    var contractor: User? = null,

    @Column(name = "contractor_id", insertable = false, updatable = false)
    var contractorId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    var client: Client? = null,

    @Column(name = "client_id", insertable = false, updatable = false)
    var clientId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    var estimate: Estimate? = null,

    @Column(name = "estimate_id", insertable = false, updatable = false)
    var estimateId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @Column(name = "project_id", insertable = false, updatable = false)
    var projectId: UUID? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class InvoiceStatus {
    draft, sent, paid, overdue, cancelled
}
