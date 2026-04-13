package com.strata.ftw.service

import com.strata.ftw.domain.entity.*
import com.strata.ftw.domain.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Service
class MarketplaceService(
    private val userRepository: UserRepository,
    private val jobRepository: JobRepository,
    private val bidRepository: BidRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val estimateRepository: EstimateRepository,
    private val invoiceRepository: InvoiceRepository,
    private val projectRepository: ProjectRepository,
    private val reviewRepository: ReviewRepository,
    private val notificationRepository: NotificationRepository,
    private val uploadRepository: UploadRepository,
    private val userSettingRepository: UserSettingRepository,
    private val fairRecordRepository: FairRecordRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val verificationRepository: VerificationRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val notificationService: NotificationService,
    private val milestoneRepository: MilestoneRepository,
    private val expenseRepository: ExpenseRepository
) {
    // ── Users ──

    fun getUser(id: UUID): User? = userRepository.findById(id).orElse(null)
    fun getUserByEmail(email: String): User? = userRepository.findByEmail(email)

    // ── Jobs ──

    fun listJobs(
        status: JobStatus? = null,
        category: String? = null,
        limit: Int = 20,
        lat: Double? = null,
        lng: Double? = null
    ): List<Job> {
        val pageable = PageRequest.of(0, limit)
        val jobs = if (status != null) {
            jobRepository.findByStatus(status, pageable)
        } else {
            jobRepository.findAllRecent(pageable)
        }
        return if (category != null) jobs.filter { it.category == category } else jobs
    }

    fun getJob(id: UUID): Job? = jobRepository.findById(id).orElse(null)

    @Transactional
    fun postJob(attrs: Map<String, Any>, homeownerId: UUID): Job {
        val homeowner = userRepository.findById(homeownerId).orElseThrow()
        val job = Job(
            title = attrs["title"] as String,
            description = attrs["description"] as? String,
            category = attrs["category"] as? String,
            budgetMin = (attrs["budget_min"] as? Number)?.toInt(),
            budgetMax = (attrs["budget_max"] as? Number)?.toInt(),
            location = attrs["location"] as? String,
            status = JobStatus.open,
            homeowner = homeowner,
            homeownerId = homeownerId
        )
        val saved = jobRepository.save(job)
        broadcast("/topic/jobs.feed", "job:posted", serializeJob(saved))
        return saved
    }

    fun listBids(jobId: UUID): List<Bid> = bidRepository.findByJobIdOrderByInsertedAtAsc(jobId)

    @Transactional
    fun placeBid(jobId: UUID, attrs: Map<String, Any>, contractorId: UUID): Bid {
        val job = jobRepository.findById(jobId).orElseThrow()
        require(job.status == JobStatus.open) { "Job is not open for bidding" }

        val existing = bidRepository.findByJobIdAndContractorId(jobId, contractorId)
        require(existing == null) { "You already placed a bid on this job" }

        val contractor = userRepository.findById(contractorId).orElseThrow()
        val bid = Bid(
            amount = (attrs["amount"] as Number).toInt(),
            message = attrs["message"] as? String,
            timeline = attrs["timeline"] as? String,
            status = BidStatus.pending,
            job = job,
            jobId = jobId,
            contractor = contractor,
            contractorId = contractorId
        )
        val saved = bidRepository.save(bid)

        job.bidCount = (job.bidCount) + 1
        jobRepository.save(job)

        broadcast("/topic/job.${jobId}", "bid:placed", serializeBid(saved))
        broadcast("/topic/jobs.feed", "job:updated", serializeJob(job))
        notificationService.onBidPlaced(job.title, contractor.name, job.homeownerId!!, jobId)
        return saved
    }

    @Transactional
    fun acceptBid(jobId: UUID, bidId: UUID, homeownerId: UUID): Bid {
        val job = jobRepository.findById(jobId).orElseThrow()
        require(job.homeownerId == homeownerId) { "Only the job owner can accept bids" }

        val bid = bidRepository.findById(bidId).orElseThrow()
        require(bid.jobId == jobId) { "Bid does not belong to this job" }

        bid.status = BidStatus.accepted
        val savedBid = bidRepository.save(bid)

        // Reject all other pending bids
        val otherBids = bidRepository.findByJobIdOrderByInsertedAtAsc(jobId)
            .filter { it.id != bidId && it.status == BidStatus.pending }
        otherBids.forEach { it.status = BidStatus.rejected; bidRepository.save(it) }

        job.status = JobStatus.awarded
        jobRepository.save(job)

        broadcast("/topic/job.${jobId}", "bid:accepted", serializeBid(savedBid))
        broadcast("/topic/jobs.feed", "job:updated", serializeJob(job))
        notificationService.onBidAccepted(job.title, bid.contractorId!!, jobId)
        // Notify rejected bidders
        otherBids.forEach { rejected ->
            notificationService.onBidRejected(job.title, rejected.contractorId!!, jobId)
        }
        return savedBid
    }

    // ── Job State Machine ──

    private val validTransitions = mapOf(
        JobStatus.open to setOf(JobStatus.awarded, JobStatus.cancelled),
        JobStatus.awarded to setOf(JobStatus.in_progress, JobStatus.cancelled),
        JobStatus.in_progress to setOf(JobStatus.completed, JobStatus.disputed),
        JobStatus.disputed to setOf(JobStatus.in_progress, JobStatus.cancelled)
    )

    @Transactional
    fun transitionJob(jobId: UUID, newStatus: JobStatus, userId: UUID): Job {
        val job = jobRepository.findById(jobId).orElseThrow()
        val allowed = validTransitions[job.status] ?: emptySet()
        require(newStatus in allowed) { "Cannot transition from ${job.status} to $newStatus" }

        job.status = newStatus
        val saved = jobRepository.save(job)

        broadcast("/topic/jobs.feed", "job:updated", serializeJob(saved))

        // Notify relevant parties
        val acceptedBid = bidRepository.findByJobIdOrderByInsertedAtAsc(jobId).find { it.status == BidStatus.accepted }
        notificationService.onJobStatusChange(job.title, newStatus.name, job.homeownerId!!, acceptedBid?.contractorId, jobId)

        if (newStatus == JobStatus.completed) {
            maybeGenerateFairRecord(saved)
        }

        return saved
    }

    private fun maybeGenerateFairRecord(job: Job) {
        // Auto-generate FairRecord on job completion if a project exists
        val project = projectRepository.findByUserIdAndStatus(
            job.homeownerId!!, ProjectStatus.active, PageRequest.of(0, 1)
        ).firstOrNull() ?: return

        if (fairRecordRepository.findByProjectId(project.id!!) != null) return
        createFairRecord(project.id!!, job)
    }

    // ── Conversations & Messages ──

    fun getConversation(id: UUID): Conversation? = conversationRepository.findById(id).orElse(null)

    fun isConversationParticipant(conversationId: UUID, userId: UUID): Boolean {
        val conv = conversationRepository.findById(conversationId).orElse(null) ?: return false
        return conv.homeownerId == userId || conv.contractorId == userId
    }

    fun getOrCreateConversation(jobId: UUID, homeownerId: UUID, contractorId: UUID): Conversation {
        return conversationRepository.findByJobIdAndHomeownerIdAndContractorId(jobId, homeownerId, contractorId)
            ?: conversationRepository.save(Conversation(
                job = userRepository.findById(homeownerId).let { null }, // lazy
                jobId = jobId,
                homeownerId = homeownerId,
                contractorId = contractorId
            ))
    }

    fun listMessages(conversationId: UUID): List<Message> =
        messageRepository.findByConversationIdOrderByInsertedAtAsc(conversationId)

    @Transactional
    fun sendMessage(conversationId: UUID, body: String, senderId: UUID): Message {
        val sender = userRepository.findById(senderId).orElseThrow()
        val message = Message(
            body = body,
            conversation = null,
            conversationId = conversationId,
            sender = sender,
            senderId = senderId
        )
        val saved = messageRepository.save(message)
        broadcast("/topic/chat.${conversationId}", "message:new", serializeMessage(saved))
        // Notify the other participant
        val conv = conversationRepository.findById(conversationId).orElse(null)
        if (conv != null) {
            val recipientId = if (conv.homeownerId == senderId) conv.contractorId else conv.homeownerId
            if (recipientId != null) {
                notificationService.onMessageReceived(sender.name, recipientId, conversationId)
            }
        }
        return saved
    }

    // ── Estimates ──

    fun listEstimates(contractorId: UUID, limit: Int = 20): List<Estimate> =
        estimateRepository.findByContractorIdOrderByInsertedAtDesc(contractorId, PageRequest.of(0, limit))

    fun getEstimate(id: UUID): Estimate? = estimateRepository.findById(id).orElse(null)

    @Transactional
    fun createEstimate(attrs: Map<String, Any>, contractorId: UUID): Estimate {
        val estimate = Estimate(
            title = attrs["title"] as String,
            description = attrs["description"] as? String,
            total = (attrs["total"] as? Number)?.toInt() ?: 0,
            notes = attrs["notes"] as? String,
            contractorId = contractorId,
            clientId = (attrs["client_id"] as? String)?.let { UUID.fromString(it) },
            jobId = (attrs["job_id"] as? String)?.let { UUID.fromString(it) }
        )
        val saved = estimateRepository.save(estimate)

        @Suppress("UNCHECKED_CAST")
        val lineItems = attrs["line_items"] as? List<Map<String, Any>> ?: emptyList()
        lineItems.forEachIndexed { index, li ->
            val item = LineItem(
                description = li["description"] as? String,
                quantity = (li["quantity"] as? Number)?.toDouble() ?: 1.0,
                unit = li["unit"] as? String,
                unitPrice = (li["unit_price"] as? Number)?.toInt() ?: 0,
                total = (li["total"] as? Number)?.toInt() ?: 0,
                category = li["category"] as? String,
                sortOrder = index,
                estimate = saved,
                estimateId = saved.id
            )
            saved.lineItems.add(item)
        }
        return estimateRepository.save(saved)
    }

    @Transactional
    fun updateEstimate(id: UUID, attrs: Map<String, Any>): Estimate {
        val estimate = estimateRepository.findById(id).orElseThrow()
        attrs["title"]?.let { estimate.title = it as String }
        attrs["description"]?.let { estimate.description = it as String }
        attrs["total"]?.let { estimate.total = (it as Number).toInt() }
        attrs["notes"]?.let { estimate.notes = it as String }
        attrs["status"]?.let { estimate.status = EstimateStatus.valueOf(it as String) }
        return estimateRepository.save(estimate)
    }

    fun deleteEstimate(id: UUID) = estimateRepository.deleteById(id)

    // ── Invoices ──

    fun listInvoices(contractorId: UUID, limit: Int = 20): List<Invoice> =
        invoiceRepository.findByContractorIdOrderByInsertedAtDesc(contractorId, PageRequest.of(0, limit))

    fun getInvoice(id: UUID): Invoice? = invoiceRepository.findById(id).orElse(null)

    @Transactional
    fun createInvoice(attrs: Map<String, Any>, contractorId: UUID): Invoice {
        val invoice = Invoice(
            invoiceNumber = attrs["invoice_number"] as String,
            amount = (attrs["amount"] as Number).toInt(),
            notes = attrs["notes"] as? String,
            dueDate = (attrs["due_date"] as? String)?.let { LocalDate.parse(it) },
            contractorId = contractorId,
            clientId = (attrs["client_id"] as? String)?.let { UUID.fromString(it) },
            estimateId = (attrs["estimate_id"] as? String)?.let { UUID.fromString(it) },
            projectId = (attrs["project_id"] as? String)?.let { UUID.fromString(it) }
        )
        return invoiceRepository.save(invoice)
    }

    @Transactional
    fun updateInvoice(id: UUID, attrs: Map<String, Any>): Invoice {
        val invoice = invoiceRepository.findById(id).orElseThrow()
        attrs["amount"]?.let { invoice.amount = (it as Number).toInt() }
        attrs["status"]?.let { invoice.status = InvoiceStatus.valueOf(it as String) }
        attrs["notes"]?.let { invoice.notes = it as String }
        attrs["due_date"]?.let { invoice.dueDate = LocalDate.parse(it as String) }
        return invoiceRepository.save(invoice)
    }

    // ── Projects ──

    fun listProjects(userId: UUID, status: ProjectStatus? = null, limit: Int = 20): List<Project> {
        val pageable = PageRequest.of(0, limit)
        return if (status != null) {
            projectRepository.findByUserIdAndStatus(userId, status, pageable)
        } else {
            projectRepository.findByUserId(userId, pageable)
        }
    }

    fun getProject(id: UUID): Project? = projectRepository.findById(id).orElse(null)

    @Transactional
    fun createProject(attrs: Map<String, Any>): Project {
        val project = Project(
            name = attrs["name"] as String,
            description = attrs["description"] as? String,
            budget = (attrs["budget"] as? Number)?.toInt() ?: 0,
            category = attrs["category"] as? String,
            contractorId = (attrs["contractor_id"] as? String)?.let { UUID.fromString(it) },
            homeownerId = (attrs["homeowner_id"] as? String)?.let { UUID.fromString(it) },
            jobId = (attrs["job_id"] as? String)?.let { UUID.fromString(it) },
            startDate = (attrs["start_date"] as? String)?.let { LocalDate.parse(it) },
            endDate = (attrs["end_date"] as? String)?.let { LocalDate.parse(it) }
        )
        val saved = projectRepository.save(project)

        // Bulk-create milestones if provided
        @Suppress("UNCHECKED_CAST")
        val milestoneList = attrs["milestones"] as? List<Map<String, Any>>
        milestoneList?.forEachIndexed { idx, m ->
            val milestone = Milestone(
                project = saved,
                title = m["title"] as String,
                description = m["description"] as? String,
                amount = (m["amount"] as? Number)?.toInt() ?: 0,
                sortOrder = (m["sort_order"] as? Number)?.toInt() ?: idx,
                dueDate = (m["due_date"] as? String)?.let { LocalDate.parse(it) }
            )
            milestoneRepository.save(milestone)
        }

        return saved
    }

    @Transactional
    fun updateProject(id: UUID, attrs: Map<String, Any>): Project {
        val project = projectRepository.findById(id).orElseThrow()
        attrs["name"]?.let { project.name = it as String }
        attrs["description"]?.let { project.description = it as String }
        attrs["status"]?.let { project.status = ProjectStatus.valueOf(it as String) }
        attrs["budget"]?.let { project.budget = (it as Number).toInt() }
        attrs["spent"]?.let { project.spent = (it as Number).toInt() }
        return projectRepository.save(project)
    }

    // ── Fair Records ──

    fun getFairRecord(id: UUID): FairRecord? = fairRecordRepository.findById(id).orElse(null)
    fun getFairRecordByPublicId(publicId: String): FairRecord? = fairRecordRepository.findByPublicId(publicId)
    fun getFairRecordByProject(projectId: UUID): FairRecord? = fairRecordRepository.findByProjectId(projectId)

    fun listContractorRecords(contractorId: UUID, limit: Int = 50): List<FairRecord> =
        fairRecordRepository.findByContractorIdAndHomeownerConfirmedOrderByInsertedAtDesc(
            contractorId, true, PageRequest.of(0, limit))

    @Transactional
    fun createFairRecord(projectId: UUID, job: Job): FairRecord {
        val project = projectRepository.findById(projectId).orElseThrow()
        val budgetAccuracy = if (project.budget > 0)
            (1.0 - abs(project.spent.toDouble() - project.budget) / project.budget) * 100
        else 0.0
        val onBudget = budgetAccuracy >= 90.0
        val onTime = project.endDate?.let { LocalDate.now() <= it } ?: true

        val reviewCount = reviewRepository.countByReviewedId(project.contractorId!!)
        val avgRating = reviewRepository.averageRatingForUser(project.contractorId!!) ?: 0.0

        val publicId = "FR-${UUID.randomUUID().toString().take(6).uppercase()}"

        val record = FairRecord(
            publicId = publicId,
            category = job.category,
            locationCity = job.location,
            scopeSummary = job.description,
            estimatedBudget = project.budget,
            finalCost = project.spent,
            budgetAccuracyPct = budgetAccuracy,
            onBudget = onBudget,
            estimatedEndDate = project.endDate,
            actualCompletionDate = LocalDate.now(),
            onTime = onTime,
            avgRating = avgRating,
            reviewCount = reviewCount.toInt(),
            projectId = projectId,
            contractorId = project.contractorId,
            homeownerId = project.homeownerId,
            jobId = job.id
        )
        return fairRecordRepository.save(record)
    }

    @Transactional
    fun confirmFairRecord(recordId: UUID, homeownerId: UUID): FairRecord {
        val record = fairRecordRepository.findById(recordId).orElseThrow()
        require(record.homeownerId == homeownerId) { "Only the homeowner can confirm" }
        record.homeownerConfirmed = true
        record.confirmedAt = Instant.now()
        return fairRecordRepository.save(record)
    }

    fun contractorRecordStats(contractorId: UUID): Map<String, Any> {
        val total = fairRecordRepository.countConfirmed(contractorId)
        val avgAccuracy = fairRecordRepository.avgBudgetAccuracy(contractorId) ?: 0.0
        val onTimeCount = fairRecordRepository.countOnTime(contractorId)
        val avgRating = reviewRepository.averageRatingForUser(contractorId) ?: 0.0
        return mapOf(
            "total" to total,
            "avg_budget_accuracy" to avgAccuracy,
            "on_time_rate" to if (total > 0) onTimeCount.toDouble() / total else 0.0,
            "avg_rating" to avgRating
        )
    }

    // ── Clients ──

    fun listClients(contractorId: UUID): List<Client> = clientRepository.findByContractorIdOrderByNameAsc(contractorId)
    fun getClient(id: UUID): Client? = clientRepository.findById(id).orElse(null)

    @Transactional
    fun createClient(attrs: Map<String, Any>, contractorId: UUID): Client {
        val client = Client(
            name = attrs["name"] as String,
            email = attrs["email"] as? String,
            phone = attrs["phone"] as? String,
            address = attrs["address"] as? String,
            notes = attrs["notes"] as? String,
            contractorId = contractorId
        )
        return clientRepository.save(client)
    }

    @Transactional
    fun updateClient(id: UUID, attrs: Map<String, Any>): Client {
        val client = clientRepository.findById(id).orElseThrow()
        attrs["name"]?.let { client.name = it as String }
        attrs["email"]?.let { client.email = it as String }
        attrs["phone"]?.let { client.phone = it as String }
        attrs["address"]?.let { client.address = it as String }
        attrs["notes"]?.let { client.notes = it as String }
        return clientRepository.save(client)
    }

    fun deleteClient(id: UUID) = clientRepository.deleteById(id)

    // ── Notifications ──

    fun listNotifications(userId: UUID, limit: Int = 50): List<Notification> =
        notificationRepository.findByUserIdOrderByInsertedAtDesc(userId, PageRequest.of(0, limit))

    @Transactional
    fun markNotificationRead(id: UUID) {
        val notif = notificationRepository.findById(id).orElseThrow()
        notif.read = true
        notificationRepository.save(notif)
    }

    @Transactional
    fun markAllNotificationsRead(userId: UUID) {
        notificationRepository.markAllRead(userId, Instant.now())
    }

    fun enqueueNotification(type: String, userId: UUID, title: String, body: String, metadata: Map<String, Any> = emptyMap()) {
        val notification = Notification(
            type = type,
            title = title,
            body = body,
            userId = userId,
            metadata = metadata
        )
        notificationRepository.save(notification)
        broadcast("/topic/user.${userId}", "notification", serializeNotification(notification))
    }

    // ── Reviews ──

    fun listReviewsForUser(userId: UUID, limit: Int = 20): List<Review> =
        reviewRepository.findByReviewedIdOrderByInsertedAtDesc(userId, PageRequest.of(0, limit))

    fun listReviewsByUser(userId: UUID): List<Review> =
        reviewRepository.findByReviewerIdOrderByInsertedAtDesc(userId)

    fun getReview(id: UUID): Review? = reviewRepository.findById(id).orElse(null)

    @Transactional
    fun createReview(attrs: Map<String, Any>, reviewerId: UUID): Review {
        val review = Review(
            rating = (attrs["rating"] as Number).toInt(),
            comment = attrs["comment"] as? String,
            reviewerId = reviewerId,
            reviewedId = UUID.fromString(attrs["reviewed_id"] as String),
            jobId = (attrs["job_id"] as? String)?.let { UUID.fromString(it) }
        )
        return reviewRepository.save(review)
    }

    @Transactional
    fun respondToReview(reviewId: UUID, response: String): Review {
        val review = reviewRepository.findById(reviewId).orElseThrow()
        review.response = response
        return reviewRepository.save(review)
    }

    // ── Uploads ──

    fun listUploads(entityType: String, entityId: UUID): List<Upload> =
        uploadRepository.findByEntityTypeAndEntityId(entityType, entityId)

    fun getUpload(id: UUID): Upload? = uploadRepository.findById(id).orElse(null)

    @Transactional
    fun createUpload(attrs: Upload): Upload = uploadRepository.save(attrs)

    fun deleteUpload(id: UUID) = uploadRepository.deleteById(id)

    // ── Settings ──

    fun getSettings(userId: UUID): UserSetting {
        return userSettingRepository.findByUserId(userId)
            ?: userSettingRepository.save(UserSetting(userId = userId))
    }

    @Transactional
    fun updateSettings(userId: UUID, attrs: Map<String, Any>): UserSetting {
        val settings = getSettings(userId)
        attrs["notifications_email"]?.let { settings.notificationsEmail = it as Boolean }
        attrs["notifications_push"]?.let { settings.notificationsPush = it as Boolean }
        attrs["notifications_sms"]?.let { settings.notificationsSms = it as Boolean }
        attrs["appearance_theme"]?.let { settings.appearanceTheme = it as String }
        attrs["language"]?.let { settings.language = it as String }
        attrs["timezone"]?.let { settings.timezone = it as String }
        attrs["privacy_profile_visible"]?.let { settings.privacyProfileVisible = it as Boolean }
        attrs["privacy_show_rating"]?.let { settings.privacyShowRating = it as Boolean }
        return userSettingRepository.save(settings)
    }

    // ── Push Tokens ──

    fun registerPushToken(userId: UUID, token: String, platform: String): PushToken {
        val existing = pushTokenRepository.findByToken(token)
        if (existing != null) {
            existing.userId = userId
            return pushTokenRepository.save(existing)
        }
        return pushTokenRepository.save(PushToken(token = token, platform = platform, userId = userId))
    }

    fun unregisterPushToken(token: String) = pushTokenRepository.deleteByToken(token)

    fun listPushTokens(userId: UUID): List<PushToken> = pushTokenRepository.findByUserId(userId)

    // ── Serializers ──

    fun serializeJob(job: Job): Map<String, Any?> = mapOf(
        "id" to job.id.toString(),
        "title" to job.title,
        "description" to job.description,
        "category" to job.category,
        "budget_min" to job.budgetMin,
        "budget_max" to job.budgetMax,
        "location" to job.location,
        "latitude" to job.latitude,
        "longitude" to job.longitude,
        "status" to job.status.name,
        "bid_count" to job.bidCount,
        "homeowner" to job.homeowner?.let { serializeUser(it) },
        "posted_at" to job.insertedAt?.toString()
    )

    fun serializeBid(bid: Bid): Map<String, Any?> = mapOf(
        "id" to bid.id.toString(),
        "job_id" to bid.jobId.toString(),
        "amount" to bid.amount,
        "message" to bid.message,
        "timeline" to bid.timeline,
        "status" to bid.status.name,
        "contractor" to bid.contractor?.let { serializeUser(it) },
        "placed_at" to bid.insertedAt?.toString()
    )

    fun serializeMessage(message: Message): Map<String, Any?> = mapOf(
        "id" to message.id.toString(),
        "conversation_id" to message.conversationId.toString(),
        "body" to message.body,
        "sender" to message.sender?.let { serializeUser(it) },
        "sent_at" to message.insertedAt?.toString()
    )

    fun serializeUser(user: User): Map<String, Any?> = mapOf(
        "id" to user.id.toString(),
        "name" to user.name,
        "role" to user.role.name,
        "location" to user.location,
        "rating" to user.rating
    )

    fun serializeEstimate(estimate: Estimate): Map<String, Any?> = mapOf(
        "id" to estimate.id.toString(),
        "title" to estimate.title,
        "description" to estimate.description,
        "total" to estimate.total,
        "status" to estimate.status.name,
        "valid_until" to estimate.validUntil?.toString(),
        "notes" to estimate.notes,
        "contractor_id" to estimate.contractorId?.toString(),
        "client_id" to estimate.clientId?.toString(),
        "job_id" to estimate.jobId?.toString(),
        "line_items" to estimate.lineItems.map { serializeLineItem(it) },
        "created_at" to estimate.insertedAt?.toString()
    )

    fun serializeLineItem(li: LineItem): Map<String, Any?> = mapOf(
        "id" to li.id.toString(),
        "description" to li.description,
        "quantity" to li.quantity,
        "unit" to li.unit,
        "unit_price" to li.unitPrice,
        "total" to li.total,
        "category" to li.category,
        "sort_order" to li.sortOrder
    )

    fun serializeInvoice(inv: Invoice): Map<String, Any?> = mapOf(
        "id" to inv.id.toString(),
        "invoice_number" to inv.invoiceNumber,
        "amount" to inv.amount,
        "status" to inv.status.name,
        "due_date" to inv.dueDate?.toString(),
        "paid_at" to inv.paidAt?.toString(),
        "notes" to inv.notes,
        "contractor_id" to inv.contractorId?.toString(),
        "client_id" to inv.clientId?.toString(),
        "estimate_id" to inv.estimateId?.toString(),
        "project_id" to inv.projectId?.toString(),
        "qb_invoice_id" to inv.qbInvoiceId,
        "qb_synced_at" to inv.qbSyncedAt?.toString(),
        "created_at" to inv.insertedAt?.toString()
    )

    // ── Milestones ──

    fun listMilestones(projectId: UUID): List<Milestone> =
        milestoneRepository.findByProjectIdOrderBySortOrderAsc(projectId)

    fun getMilestone(id: UUID): Milestone? = milestoneRepository.findById(id).orElse(null)

    @Transactional
    fun createMilestone(projectId: UUID, attrs: Map<String, Any>): Milestone {
        val project = projectRepository.findById(projectId).orElseThrow()
        val milestone = Milestone(
            project = project,
            title = attrs["title"] as String,
            description = attrs["description"] as? String,
            amount = (attrs["amount"] as? Number)?.toInt() ?: 0,
            sortOrder = (attrs["sort_order"] as? Number)?.toInt() ?: 0,
            dueDate = (attrs["due_date"] as? String)?.let { LocalDate.parse(it) }
        )
        return milestoneRepository.save(milestone)
    }

    @Transactional
    fun updateMilestone(id: UUID, attrs: Map<String, Any>): Milestone {
        val milestone = milestoneRepository.findById(id).orElseThrow()
        attrs["title"]?.let { milestone.title = it as String }
        attrs["description"]?.let { milestone.description = it as String }
        attrs["amount"]?.let { milestone.amount = (it as Number).toInt() }
        attrs["sort_order"]?.let { milestone.sortOrder = (it as Number).toInt() }
        attrs["note"]?.let { milestone.note = it as String }
        attrs["due_date"]?.let { milestone.dueDate = LocalDate.parse(it as String) }
        attrs["status"]?.let {
            val newStatus = MilestoneStatus.valueOf(it as String)
            milestone.status = newStatus
            when (newStatus) {
                MilestoneStatus.complete -> milestone.completedDate = LocalDate.now()
                MilestoneStatus.paid -> milestone.paidDate = LocalDate.now()
                else -> {}
            }
        }
        return milestoneRepository.save(milestone)
    }

    @Transactional
    fun deleteMilestone(id: UUID) = milestoneRepository.deleteById(id)

    // ── Expenses ──

    fun listExpenses(projectId: UUID): List<Expense> =
        expenseRepository.findByProjectIdOrderByDateDesc(projectId)

    @Transactional
    fun createExpense(projectId: UUID, attrs: Map<String, Any>): Expense {
        val project = projectRepository.findById(projectId).orElseThrow()
        val expense = Expense(
            project = project,
            description = attrs["description"] as String,
            amount = (attrs["amount"] as Number).toInt(),
            category = attrs["category"] as? String,
            date = (attrs["date"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            vendor = attrs["vendor"] as? String
        )
        (attrs["milestone_id"] as? String)?.let { msId ->
            expense.milestone = milestoneRepository.findById(UUID.fromString(msId)).orElse(null)
        }
        val saved = expenseRepository.save(expense)
        // Update project spent total
        project.spent = project.spent + saved.amount
        projectRepository.save(project)
        return saved
    }

    @Transactional
    fun deleteExpense(id: UUID) {
        val expense = expenseRepository.findById(id).orElse(null) ?: return
        expense.projectId?.let { pid ->
            val project = projectRepository.findById(pid).orElse(null)
            if (project != null) {
                project.spent = (project.spent - expense.amount).coerceAtLeast(0)
                projectRepository.save(project)
            }
        }
        expenseRepository.deleteById(id)
    }

    // ── Serializers ──

    fun serializeProject(proj: Project): Map<String, Any?> = mapOf(
        "id" to proj.id.toString(),
        "name" to proj.name,
        "description" to proj.description,
        "status" to proj.status.name,
        "category" to proj.category,
        "start_date" to proj.startDate?.toString(),
        "end_date" to proj.endDate?.toString(),
        "budget" to proj.budget,
        "spent" to proj.spent,
        "contractor_id" to proj.contractorId?.toString(),
        "homeowner_id" to proj.homeownerId?.toString(),
        "job_id" to proj.jobId?.toString(),
        "milestones" to listMilestones(proj.id!!).map { serializeMilestone(it) },
        "expenses" to listExpenses(proj.id!!).map { serializeExpense(it) },
        "created_at" to proj.insertedAt?.toString()
    )

    fun serializeMilestone(m: Milestone): Map<String, Any?> = mapOf(
        "id" to m.id.toString(),
        "project_id" to m.projectId?.toString(),
        "title" to m.title,
        "description" to m.description,
        "amount" to m.amount,
        "status" to m.status.name,
        "sort_order" to m.sortOrder,
        "due_date" to m.dueDate?.toString(),
        "completed_date" to m.completedDate?.toString(),
        "paid_date" to m.paidDate?.toString(),
        "note" to m.note,
        "created_at" to m.insertedAt?.toString()
    )

    fun serializeExpense(e: Expense): Map<String, Any?> = mapOf(
        "id" to e.id.toString(),
        "project_id" to e.projectId?.toString(),
        "milestone_id" to e.milestoneId?.toString(),
        "description" to e.description,
        "amount" to e.amount,
        "category" to e.category,
        "date" to e.date.toString(),
        "vendor" to e.vendor,
        "created_at" to e.insertedAt?.toString()
    )

    fun serializeClient(client: Client): Map<String, Any?> = mapOf(
        "id" to client.id.toString(),
        "name" to client.name,
        "email" to client.email,
        "phone" to client.phone,
        "address" to client.address,
        "notes" to client.notes,
        "created_at" to client.insertedAt?.toString()
    )

    fun serializeReview(review: Review): Map<String, Any?> = mapOf(
        "id" to review.id.toString(),
        "rating" to review.rating,
        "comment" to review.comment,
        "response" to review.response,
        "reviewer_id" to review.reviewerId?.toString(),
        "reviewed_id" to review.reviewedId?.toString(),
        "job_id" to review.jobId?.toString(),
        "created_at" to review.insertedAt?.toString()
    )

    fun serializeNotification(notif: Notification): Map<String, Any?> = mapOf(
        "id" to notif.id.toString(),
        "type" to notif.type,
        "title" to notif.title,
        "body" to notif.body,
        "read" to notif.read,
        "metadata" to notif.metadata,
        "created_at" to notif.insertedAt?.toString()
    )

    fun serializeFairRecord(record: FairRecord): Map<String, Any?> = mapOf(
        "id" to record.id.toString(),
        "public_id" to record.publicId,
        "category" to record.category,
        "location_city" to record.locationCity,
        "scope_summary" to record.scopeSummary,
        "estimated_budget" to record.estimatedBudget,
        "final_cost" to record.finalCost,
        "budget_accuracy_pct" to record.budgetAccuracyPct,
        "on_budget" to record.onBudget,
        "estimated_end_date" to record.estimatedEndDate?.toString(),
        "actual_completion_date" to record.actualCompletionDate?.toString(),
        "on_time" to record.onTime,
        "quality_score_at_completion" to record.qualityScoreAtCompletion,
        "avg_rating" to record.avgRating,
        "review_count" to record.reviewCount,
        "dispute_count" to record.disputeCount,
        "photos" to record.photos,
        "homeowner_confirmed" to record.homeownerConfirmed,
        "confirmed_at" to record.confirmedAt?.toString(),
        "contractor_id" to record.contractorId?.toString(),
        "homeowner_id" to record.homeownerId?.toString(),
        "project_id" to record.projectId?.toString(),
        "job_id" to record.jobId?.toString(),
        "created_at" to record.insertedAt?.toString()
    )

    // ── Broadcasting ──

    private fun broadcast(destination: String, eventType: String, payload: Map<String, Any?>) {
        messagingTemplate.convertAndSend(destination, mapOf("event" to eventType, "data" to payload))
    }

    // ── Haversine Distance ──

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * acos(minOf(1.0, Math.sqrt(a) * 2.let { 1.0 } ).let {
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        })
    }
}
