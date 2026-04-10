package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "receipts")
class Receipt(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "receipt_number", nullable = false, unique = true)
    var receiptNumber: String = "",

    @Column(name = "bid_id", nullable = false, unique = true)
    var bidId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id", insertable = false, updatable = false)
    var bid: Bid? = null,

    @Column(name = "gross_amount", nullable = false)
    var grossAmount: Int = 0,

    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int = 0,

    @Column(name = "total_charged", nullable = false)
    var totalCharged: Int = 0,

    @Column(name = "job_title", nullable = false)
    var jobTitle: String = "",

    @Column(name = "contractor_name", nullable = false)
    var contractorName: String = "",

    @Column(name = "homeowner_name", nullable = false)
    var homeownerName: String = "",

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null
)
