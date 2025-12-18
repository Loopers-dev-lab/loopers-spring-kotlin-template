package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        Index(
            name = "idx_products_price",
            columnList = "price ASC, id DESC",
        ),
        Index(
            name = "idx_products_brand_id",
            columnList = "brand_id",
        ),
    ],
)
class Product(
    brandId: Long,
    name: String,
    price: Money,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        private set

    @Column(name = "name", nullable = false)
    var name: String = name
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = price
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProductSaleStatus = ProductSaleStatus.ON_SALE
        private set

    fun updateSaleStatus(stockQuantity: Int) {
        this.status = if (stockQuantity == 0) ProductSaleStatus.SOLD_OUT else ProductSaleStatus.ON_SALE
    }

    companion object {
        fun create(
            name: String,
            price: Money,
            brand: Brand,
        ): Product {
            return Product(
                brandId = brand.id,
                name = name,
                price = price,
            )
        }

        fun of(
            brandId: Long,
            name: String,
            price: Money,
        ): Product {
            return Product(
                brandId = brandId,
                name = name,
                price = price,
            )
        }
    }
}
