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
        fun create(name: String, price: Long, brandId: Long): Product {
            require(price > 0) { "상품 가격은 0보다 커야 합니다." }

            return Product(
                name = name,
                price = price,
                brandId = brandId,
            )
        }
    }
}
