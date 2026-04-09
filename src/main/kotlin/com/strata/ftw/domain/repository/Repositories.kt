package com.strata.ftw.domain.repository

import com.strata.ftw.domain.entity.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role) AND (:active IS NULL OR u.active = :active) ORDER BY u.insertedAt DESC")
    fun findFiltered(role: UserRole?, active: Boolean?, pageable: Pageable): List<User>

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    fun countByRole(role: UserRole): Long
}

@Repository
interface JobRepository : JpaRepository<Job, UUID> {
    fun findByStatusOrderByInsertedAtDesc(status: JobStatus, pageable: Pageable): List<Job>
    fun findByHomeownerIdOrderByInsertedAtDesc(homeownerId: UUID): List<Job>

    @Query("SELECT j FROM Job j WHERE j.status = :status ORDER BY j.insertedAt DESC")
    fun findByStatus(status: JobStatus, pageable: Pageable): List<Job>

    @Query("SELECT j FROM Job j ORDER BY j.insertedAt DESC")
    fun findAllRecent(pageable: Pageable): List<Job>
}

@Repository
interface BidRepository : JpaRepository<Bid, UUID> {
    fun findByJobIdOrderByInsertedAtAsc(jobId: UUID): List<Bid>
    fun findByContractorId(contractorId: UUID): List<Bid>
    fun findByJobIdAndContractorId(jobId: UUID, contractorId: UUID): Bid?

    @Modifying
    @Query("UPDATE Bid b SET b.status = :status, b.updatedAt = :now WHERE b.jobId = :jobId AND b.id != :excludeId AND b.status = 'pending'")
    fun rejectOtherBids(jobId: UUID, excludeId: UUID, status: BidStatus, now: Instant)
}

@Repository
interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByJobIdAndHomeownerIdAndContractorId(jobId: UUID, homeownerId: UUID, contractorId: UUID): Conversation?

    @Query("SELECT c FROM Conversation c WHERE c.homeownerId = :userId OR c.contractorId = :userId ORDER BY c.updatedAt DESC")
    fun findByParticipant(userId: UUID): List<Conversation>
}

@Repository
interface MessageRepository : JpaRepository<Message, UUID> {
    fun findByConversationIdOrderByInsertedAtAsc(conversationId: UUID): List<Message>
}

@Repository
interface ClientRepository : JpaRepository<Client, UUID> {
    fun findByContractorIdOrderByNameAsc(contractorId: UUID): List<Client>
}

@Repository
interface EstimateRepository : JpaRepository<Estimate, UUID> {
    fun findByContractorIdOrderByInsertedAtDesc(contractorId: UUID, pageable: Pageable): List<Estimate>
}

@Repository
interface LineItemRepository : JpaRepository<LineItem, UUID> {
    fun findByEstimateIdOrderBySortOrderAsc(estimateId: UUID): List<LineItem>
}

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    fun findByContractorIdOrderByInsertedAtDesc(contractorId: UUID, pageable: Pageable): List<Invoice>
}

@Repository
interface ProjectRepository : JpaRepository<Project, UUID> {
    @Query("SELECT p FROM Project p WHERE (p.contractorId = :userId OR p.homeownerId = :userId) ORDER BY p.insertedAt DESC")
    fun findByUserId(userId: UUID, pageable: Pageable): List<Project>

    @Query("SELECT p FROM Project p WHERE (p.contractorId = :userId OR p.homeownerId = :userId) AND p.status = :status ORDER BY p.insertedAt DESC")
    fun findByUserIdAndStatus(userId: UUID, status: ProjectStatus, pageable: Pageable): List<Project>
}

@Repository
interface ReviewRepository : JpaRepository<Review, UUID> {
    fun findByReviewedIdOrderByInsertedAtDesc(reviewedId: UUID, pageable: Pageable): List<Review>
    fun findByReviewerIdOrderByInsertedAtDesc(reviewerId: UUID): List<Review>

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewedId = :userId")
    fun averageRatingForUser(userId: UUID): Double?

    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewedId = :userId")
    fun countByReviewedId(userId: UUID): Long

    fun findByReviewerIdAndJobId(reviewerId: UUID, jobId: UUID): Review?

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.reviewedId = :userId GROUP BY r.rating ORDER BY r.rating DESC")
    fun ratingBreakdown(userId: UUID): List<Array<Any>>
}

@Repository
interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByInsertedAtDesc(userId: UUID, pageable: Pageable): List<Notification>

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.updatedAt = :now WHERE n.userId = :userId AND n.read = false")
    fun markAllRead(userId: UUID, now: Instant)
}

@Repository
interface UploadRepository : JpaRepository<Upload, UUID> {
    fun findByEntityTypeAndEntityId(entityType: String, entityId: UUID): List<Upload>
    fun findByUploaderId(uploaderId: UUID): List<Upload>
}

@Repository
interface UserSettingRepository : JpaRepository<UserSetting, UUID> {
    fun findByUserId(userId: UUID): UserSetting?
}

@Repository
interface VerificationRepository : JpaRepository<Verification, UUID> {
    fun findByContractorId(contractorId: UUID): List<Verification>
    fun findByContractorIdAndStep(contractorId: UUID, step: String): Verification?
    fun findByStatus(status: String): List<Verification>

    @Query("SELECT v FROM Verification v WHERE v.status = 'approved' AND v.expiresAt < :now")
    fun findExpired(now: Instant): List<Verification>

    @Query("SELECT v FROM Verification v WHERE v.status = 'pending' ORDER BY v.insertedAt ASC")
    fun findPending(): List<Verification>
}

@Repository
interface FairRecordRepository : JpaRepository<FairRecord, UUID> {
    fun findByPublicId(publicId: String): FairRecord?
    fun findByProjectId(projectId: UUID): FairRecord?
    fun findByContractorIdAndHomeownerConfirmedOrderByInsertedAtDesc(
        contractorId: UUID, confirmed: Boolean, pageable: Pageable
    ): List<FairRecord>

    @Query("SELECT COUNT(fr) FROM FairRecord fr WHERE fr.contractorId = :cid AND fr.homeownerConfirmed = true")
    fun countConfirmed(cid: UUID): Long

    @Query("SELECT AVG(fr.budgetAccuracyPct) FROM FairRecord fr WHERE fr.contractorId = :cid AND fr.homeownerConfirmed = true")
    fun avgBudgetAccuracy(cid: UUID): Double?

    @Query("SELECT COUNT(fr) FROM FairRecord fr WHERE fr.contractorId = :cid AND fr.homeownerConfirmed = true AND fr.onTime = true")
    fun countOnTime(cid: UUID): Long
}

@Repository
interface PushTokenRepository : JpaRepository<PushToken, UUID> {
    fun findByUserId(userId: UUID): List<PushToken>
    fun findByToken(token: String): PushToken?
    fun deleteByToken(token: String)
}

@Repository
interface ContentFlagRepository : JpaRepository<ContentFlag, UUID> {
    fun findByStatusOrderByInsertedAtAsc(status: String, pageable: Pageable): List<ContentFlag>
}

@Repository
interface DisputeRepository : JpaRepository<Dispute, UUID> {
    fun findByJobId(jobId: UUID): List<Dispute>
    fun countByJobId(jobId: UUID): Long

    @Query("SELECT d FROM Dispute d ORDER BY d.insertedAt DESC")
    fun findAllRecent(pageable: Pageable): List<Dispute>

    fun findByStatus(status: String): List<Dispute>
}

@Repository
interface DisputeEvidenceRepository : JpaRepository<DisputeEvidence, UUID> {
    fun findByDisputeId(disputeId: UUID): List<DisputeEvidence>
}

@Repository
interface RevenueSnapshotRepository : JpaRepository<RevenueSnapshot, UUID> {
    fun findByDate(date: LocalDate): RevenueSnapshot?
}

@Repository
interface TransactionLogRepository : JpaRepository<TransactionLog, UUID>

@Repository
interface FairPriceEntryRepository : JpaRepository<FairPriceEntry, UUID> {
    fun findByCategoryAndZipPrefixAndSize(category: String, zipPrefix: String, size: String): FairPriceEntry?
}

@Repository
interface SubContractorRepository : JpaRepository<SubContractor, UUID> {
    fun findByUserId(userId: UUID): SubContractor?
}

@Repository
interface SubJobRepository : JpaRepository<SubJob, UUID> {
    fun findByStatusOrderByInsertedAtDesc(status: SubJobStatus, pageable: Pageable): List<SubJob>
    fun findByContractorIdOrderByInsertedAtDesc(contractorId: UUID): List<SubJob>

    @Query("SELECT sj FROM SubJob sj WHERE sj.status = :status ORDER BY sj.insertedAt DESC")
    fun findByStatus(status: SubJobStatus, pageable: Pageable): List<SubJob>

    @Query("SELECT sj FROM SubJob sj ORDER BY sj.insertedAt DESC")
    fun findAllRecent(pageable: Pageable): List<SubJob>

    @Query("SELECT COUNT(sj) FROM SubJob sj WHERE sj.contractorId = :uid AND sj.status = :status")
    fun countByContractorIdAndStatus(uid: UUID, status: SubJobStatus): Long
}

@Repository
interface SubBidRepository : JpaRepository<SubBid, UUID> {
    fun findBySubJobIdOrderByInsertedAtAsc(subJobId: UUID): List<SubBid>
    fun findBySubContractorId(subContractorId: UUID): List<SubBid>
    fun findBySubJobIdAndSubContractorId(subJobId: UUID, subContractorId: UUID): SubBid?

    @Query("SELECT COUNT(sb) FROM SubBid sb WHERE sb.subContractorId = :uid AND sb.status = 'pending'")
    fun countPendingBySubContractorId(uid: UUID): Long

    @Query("SELECT COUNT(sb) FROM SubBid sb WHERE sb.subContractorId = :uid AND sb.status = 'accepted'")
    fun countAcceptedBySubContractorId(uid: UUID): Long
}

@Repository
interface SubPayoutRepository : JpaRepository<SubPayout, UUID> {
    fun findBySubJobId(subJobId: UUID): SubPayout?
    fun findByStatus(status: String): List<SubPayout>

    @Query("SELECT sp FROM SubPayout sp WHERE sp.status IN ('failed', 'processing') AND sp.updatedAt < :cutoff")
    fun findRetryable(cutoff: Instant): List<SubPayout>

    @Query("SELECT COALESCE(SUM(sp.netAmount), 0) FROM SubPayout sp WHERE sp.subContractorId = :uid AND sp.status = 'paid' AND sp.paidAt >= :since")
    fun sumNetAmountSince(uid: UUID, since: Instant): Double

    @Query("SELECT COALESCE(SUM(sp.netAmount), 0) FROM SubPayout sp WHERE sp.subContractorId = :uid AND sp.status = 'paid' AND sp.paidAt >= :since AND sp.paidAt < :until")
    fun sumNetAmountBetween(uid: UUID, since: Instant, until: Instant): Double
}

@Repository
interface QbCredentialRepository : JpaRepository<QbCredential, UUID> {
    fun findByUserId(userId: UUID): QbCredential?
}
