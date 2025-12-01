package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product",
    indexes = [
        Index(name = "idx_product_brand_id", columnList = "ref_brand_id"),
        Index(name = "idx_product_brand_id_created_at_id", columnList = "ref_brand_id, created_at DESC"),
        Index(name = "idx_product_brand_id_price_id", columnList = "ref_brand_id, price"),
    ],
)
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
