package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payouts")
class Payout(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "bid_id", nullable = false, unique = true)
    var bidId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id", insertable = false, updatable = false)
    var bid: Bid? = null,

    @Column(name = "gross_amount", nullable = false)
    var grossAmount: Int = 0,

    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int = 0,

    @Column(name = "net_amount", nullable = false)
    var netAmount: Int = 0,

    @Column(name = "fee_percent", nullable = false)
    var feePercent: Double = 5.0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PayoutStatus = PayoutStatus.PENDING,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "qb_bill_id")
    var qbBillId: String? = null,

    @Column(name = "qb_bill_payment_id")
    var qbBillPaymentId: String? = null,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class PayoutStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
