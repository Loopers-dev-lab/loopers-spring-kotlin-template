package com.loopers.domain.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.vo.ProductDescription
import com.loopers.domain.product.vo.ProductName
import com.loopers.domain.product.vo.ProductPrice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "product")
class Product protected constructor(
    brandId: Long,
    name: ProductName,
    description: ProductDescription,
    price: ProductPrice,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(name = "name", nullable = false)
    var name: ProductName = name
        protected set

    @Column(name = "description", nullable = false)
    var description: ProductDescription = description
        protected set

    @Column(name = "price", nullable = false)
    var price: ProductPrice = price
        protected set

    fun update(name: String, description: String, price: BigDecimal) {
        this.name = ProductName(name)
        this.description = ProductDescription(description)
        this.price = ProductPrice(price)
        this.updatedAt = ZonedDateTime.now()
    }

    companion object {
        fun create(brandId: Long, name: String, description: String, price: BigDecimal): Product {
            return Product(brandId, ProductName(name), ProductDescription(description), ProductPrice(price))
        }
    }
}
