package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product")
class Product(

    @Column(nullable = false, unique = true, columnDefinition = "varchar(100)")
    val name: String,

    @Column(nullable = false)
    val price: Long,

    @Column(name = "ref_brand_id", nullable = false)
    val brandId: Long,

    ) : BaseEntity() {
    companion object {
        fun create(name: String, brandId: Long): Product {
            return Product(
                name = name,
                price = 0L,
                brandId = brandId,
            )
        }
    }
}
