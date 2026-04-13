package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sub_payouts")
class SubPayout(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "sub_job_id", nullable = false, unique = true)
    var subJobId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_job_id", insertable = false, updatable = false)
    var subJob: SubJob? = null,

    @Column(name = "sub_contractor_id", nullable = false)
    var subContractorId: UUID? = null,

    @Column(name = "gross_amount", nullable = false)
    var grossAmount: Double = 0.0, // Amount in dollars (NOTE: unlike Payout, this uses Double not Int cents)

    @Column(name = "platform_fee", nullable = false)
    var platformFee: Double = 0.0, // Amount in dollars (NOTE: unlike Payout, this uses Double not Int cents)

    @Column(name = "net_amount", nullable = false)
    var netAmount: Double = 0.0, // Amount in dollars (NOTE: unlike Payout, this uses Double not Int cents)

    @Column(name = "fee_percent")
    var feePercent: Double = 5.0,

    @Column(name = "payment_path", nullable = false)
    @Enumerated(EnumType.STRING)
    var paymentPath: SubPaymentPath = SubPaymentPath.contractor_escrow,

    var status: String = "queued",

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
