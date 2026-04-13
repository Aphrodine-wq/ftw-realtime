package com.strata.ftw.config

import com.strata.ftw.domain.entity.*
import com.strata.ftw.domain.repository.*
import com.strata.ftw.service.AuthService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Component
@Profile("dev")
class DataSeeder(
    private val userRepository: UserRepository,
    private val jobRepository: JobRepository,
    private val bidRepository: BidRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val reviewRepository: ReviewRepository,
    private val invoiceRepository: InvoiceRepository,
    private val subContractorRepository: SubContractorRepository,
    private val authService: AuthService
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String?) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded, skipping")
            return
        }

        log.info("Seeding dev database with Mississippi construction data...")

        // ── Homeowners ──
        val h1 = createUser("sarah.johnson@example.com", "Sarah Johnson", UserRole.homeowner, "Oxford, MS", 34.3665, -89.5192)
        val h2 = createUser("marcus.williams@example.com", "Marcus Williams", UserRole.homeowner, "Tupelo, MS", 34.2576, -88.7034)
        val h3 = createUser("lisa.chen@example.com", "Lisa Chen", UserRole.homeowner, "Hattiesburg, MS", 31.3271, -89.2903)

        // ── Contractors ──
        val c1 = createUser("billy.wright@example.com", "Billy Wright", UserRole.contractor, "Oxford, MS", 34.3665, -89.5192,
            licenseNumber = "MS-GC-2024-1847", rating = 4.8, jobsCompleted = 47, qualityScore = 92)
        val c2 = createUser("james.tucker@example.com", "James Tucker", UserRole.contractor, "Tupelo, MS", 34.2576, -88.7034,
            licenseNumber = "MS-GC-2023-0932", rating = 4.5, jobsCompleted = 31, qualityScore = 85)
        val c3 = createUser("david.patel@example.com", "David Patel", UserRole.contractor, "Hattiesburg, MS", 31.3271, -89.2903,
            licenseNumber = "MS-ELEC-2024-2201", rating = 4.9, jobsCompleted = 63, qualityScore = 96)
        val c4 = createUser("mike.hernandez@example.com", "Mike Hernandez", UserRole.contractor, "Starkville, MS", 33.4504, -88.8184,
            licenseNumber = "MS-PLM-2022-1103", rating = 4.3, jobsCompleted = 22, qualityScore = 78)
        val c5 = createUser("tom.baker@example.com", "Tom Baker", UserRole.contractor, "Jackson, MS", 32.2988, -90.1848,
            licenseNumber = "MS-GC-2024-3305", rating = 4.7, jobsCompleted = 55, qualityScore = 90)

        // ── Subcontractors ──
        val s1 = createUser("carlos.rivera@example.com", "Carlos Rivera", UserRole.sub_contractor, "Oxford, MS", 34.3665, -89.5192)
        val s2 = createUser("dan.foster@example.com", "Dan Foster", UserRole.sub_contractor, "Tupelo, MS", 34.2576, -88.7034)
        val s3 = createUser("ray.thompson@example.com", "Ray Thompson", UserRole.sub_contractor, "Hattiesburg, MS", 31.3271, -89.2903)

        // SubContractor profiles
        subContractorRepository.save(SubContractor(userId = s1.id, company = "Rivera Framing", specialty = "framing", skills = "framing,drywall,carpentry", location = "Oxford, MS", yearsExperience = 12, hourlyRate = 4500, licensed = true, insured = true))
        subContractorRepository.save(SubContractor(userId = s2.id, company = "Foster Electric", specialty = "electrical", skills = "electrical,panel_upgrades,wiring", location = "Tupelo, MS", yearsExperience = 8, hourlyRate = 5500, licensed = true, insured = true))
        subContractorRepository.save(SubContractor(userId = s3.id, company = "Thompson Plumbing", specialty = "plumbing", skills = "plumbing,water_heaters,pipe_fitting", location = "Hattiesburg, MS", yearsExperience = 15, hourlyRate = 5000, licensed = true, insured = true))

        // ── Jobs (mix of statuses) ──
        val j1 = createJob("Kitchen Remodel — Full Gut", "Complete kitchen renovation including cabinets, countertops, flooring, plumbing, and electrical. 200 sq ft galley kitchen.", "remodeling", 25000, 45000, "Oxford, MS", JobStatus.open, h1)
        val j2 = createJob("Roof Replacement — Storm Damage", "Full tear-off and reroof. 2400 sq ft ranch home. Insurance claim filed, adjuster approved.", "roofing", 8000, 14000, "Tupelo, MS", JobStatus.awarded, h2)
        val j3 = createJob("Deck Build — Composite 16x20", "New composite deck with railing, stairs, and pergola. Includes permit.", "carpentry", 12000, 20000, "Hattiesburg, MS", JobStatus.in_progress, h3)
        val j4 = createJob("Bathroom Tile and Fixture Update", "Re-tile shower, replace vanity and toilet. Master bathroom.", "remodeling", 5000, 9000, "Oxford, MS", JobStatus.completed, h1)
        val j5 = createJob("Whole-House Electrical Panel Upgrade", "Upgrade from 100A to 200A panel. Bring up to current code. 1960s ranch.", "electrical", 3000, 6000, "Tupelo, MS", JobStatus.open, h2)

        // ── Bids ──
        createBid(j1, c1, 35000, "We can start in two weeks. Includes demolition, new cabinets from local supplier, granite countertops.", "4-6 weeks")
        createBid(j1, c2, 38500, "Full renovation with custom cabinetry. We use all union labor.", "6-8 weeks")
        createBid(j1, c5, 32000, "Competitive pricing. We've done 15+ kitchens in the Oxford area this year.", "5-7 weeks")

        createBid(j2, c1, 11500, "Full tear-off, ice/water shield, 30-year architectural shingles. 10-year workmanship warranty.", "3-4 days")
        createBid(j2, c4, 10200, "We specialize in storm damage repairs. Direct insurance billing available.", "2-3 days")

        createBid(j3, c5, 16500, "TimberTech composite with aluminum railing. Includes full engineering drawings.", "2-3 weeks")
        createBid(j3, c1, 18000, "Trex Transcend decking. Premium build with hidden fasteners.", "3 weeks")

        createBid(j4, c3, 7200, "Full tile work, new Kohler fixtures. We handle the permit.", "1-2 weeks")

        createBid(j5, c3, 4500, "200A Square D panel. Full inspection and code compliance cert.", "1-2 days")
        createBid(j5, c4, 5100, "Including whole-house surge protection. Eaton BR panel.", "2-3 days")

        // ── Conversations with messages ──
        val conv1 = conversationRepository.save(Conversation(jobId = j1.id, homeownerId = h1.id, contractorId = c1.id))
        messageRepository.save(Message(body = "Hey Billy, I saw your bid. Can you tell me more about the cabinet supplier you use?", conversationId = conv1.id, senderId = h1.id))
        messageRepository.save(Message(body = "We work with Oxford Cabinet Co — local shop, solid wood. I can send you their catalog if you want.", conversationId = conv1.id, senderId = c1.id))
        messageRepository.save(Message(body = "That would be great. Also, does your bid include the backsplash tile?", conversationId = conv1.id, senderId = h1.id))

        val conv2 = conversationRepository.save(Conversation(jobId = j2.id, homeownerId = h2.id, contractorId = c1.id))
        messageRepository.save(Message(body = "Billy, can you work with my insurance adjuster? They want to review the scope before we start.", conversationId = conv2.id, senderId = h2.id))
        messageRepository.save(Message(body = "Absolutely. I deal with State Farm and Allstate claims all the time. Send me the claim number and I'll coordinate.", conversationId = conv2.id, senderId = c1.id))

        val conv3 = conversationRepository.save(Conversation(jobId = j3.id, homeownerId = h3.id, contractorId = c5.id))
        messageRepository.save(Message(body = "Tom, are we still on track for the permit review next Tuesday?", conversationId = conv3.id, senderId = h3.id))
        messageRepository.save(Message(body = "Yes ma'am. Inspector is scheduled for 10am. I'll be there to walk it with him.", conversationId = conv3.id, senderId = c5.id))

        // ── Invoices ──
        invoiceRepository.save(Invoice(invoiceNumber = "INV-2026-001", amount = 720000, status = InvoiceStatus.paid, contractorId = c3.id, clientId = null, dueDate = LocalDate.of(2026, 3, 15), paidAt = Instant.now()))
        invoiceRepository.save(Invoice(invoiceNumber = "INV-2026-002", amount = 1650000, status = InvoiceStatus.sent, contractorId = c5.id, clientId = null, dueDate = LocalDate.of(2026, 4, 30)))
        invoiceRepository.save(Invoice(invoiceNumber = "INV-2026-003", amount = 350000, status = InvoiceStatus.draft, contractorId = c1.id, clientId = null))

        // ── Reviews ──
        reviewRepository.save(Review(rating = 5, comment = "Billy did an incredible job on our bathroom. On time, on budget, and the tile work is flawless.", reviewerId = h1.id, reviewedId = c1.id, jobId = j4.id))
        reviewRepository.save(Review(rating = 4, comment = "Good work overall. Communication could have been a bit more proactive but the result was solid.", reviewerId = h2.id, reviewedId = c1.id))
        reviewRepository.save(Review(rating = 5, comment = "David's electrical work is top notch. He found wiring issues nobody else caught.", reviewerId = h1.id, reviewedId = c3.id))

        log.info("Dev seed complete: {} users, {} jobs, {} bids, {} conversations",
            userRepository.count(), jobRepository.count(), bidRepository.count(), conversationRepository.count())
    }

    private fun createUser(
        email: String, name: String, role: UserRole, location: String,
        lat: Double, lng: Double, licenseNumber: String? = null,
        rating: Double = 0.0, jobsCompleted: Int = 0, qualityScore: Int? = null
    ): User {
        val user = User(
            email = email,
            name = name,
            role = role,
            activeRole = role,
            roles = role.name,
            location = location,
            latitude = lat,
            longitude = lng,
            passwordHash = authService.hashPassword("password123"),
            licenseNumber = licenseNumber,
            rating = rating,
            jobsCompleted = jobsCompleted,
            qualityScore = qualityScore
        )
        return userRepository.save(user)
    }

    private fun createJob(
        title: String, description: String, category: String,
        budgetMin: Int, budgetMax: Int, location: String,
        status: JobStatus, homeowner: User
    ): Job {
        val job = Job(
            title = title,
            description = description,
            category = category,
            budgetMin = budgetMin,
            budgetMax = budgetMax,
            location = location,
            status = status,
            homeowner = homeowner,
            homeownerId = homeowner.id
        )
        return jobRepository.save(job)
    }

    private fun createBid(job: Job, contractor: User, amount: Int, message: String, timeline: String): Bid {
        val bid = Bid(
            amount = amount,
            message = message,
            timeline = timeline,
            status = if (job.status == JobStatus.awarded || job.status == JobStatus.in_progress || job.status == JobStatus.completed) BidStatus.accepted else BidStatus.pending,
            job = job,
            jobId = job.id,
            contractor = contractor,
            contractorId = contractor.id
        )
        val saved = bidRepository.save(bid)
        job.bidCount = job.bidCount + 1
        jobRepository.save(job)
        return saved
    }
}
