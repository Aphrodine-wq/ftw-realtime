package com.strata.ftw.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "materials")
class Material(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "material_key", nullable = false, unique = true)
    var materialKey: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var category: MaterialCategory = MaterialCategory.lumber,

    var subcategory: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var unit: MaterialUnit = MaterialUnit.each,

    var dimensions: String? = null,
    var brand: String? = null,
    var sku: String? = null,

    /**
     * Multi-tenant scoping. NULL or DEFAULT_ID = the legacy single-tenant
     * deployment. New API key holders write under their tenant.
     */
    @Column(name = "tenant_id", columnDefinition = "uuid")
    var tenantId: UUID? = Tenant.DEFAULT_ID,

    @CreationTimestamp
    @Column(name = "inserted_at", updatable = false)
    var insertedAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)

enum class MaterialCategory {
    lumber, concrete, steel, drywall, insulation, roofing
}

enum class MaterialUnit {
    each, board_ft, sq_ft, cu_yd, ton, lin_ft, sheet, bag, bundle, box, lb
}
