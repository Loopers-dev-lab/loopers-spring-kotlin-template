package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "products",
    indexes = [
        Index(name = "idx_products_brand_likes", columnList = "brand_id, likes_count DESC"),
        Index(name = "idx_products_deleted_likes", columnList = "deleted_at, likes_count DESC")
    ]
)
class Product(
    name: String,
    description: String?,
    price: Money,
    stock: Stock,
    brandId: Long,
) : BaseEntity() {

    @Column(name = "name", nullable = false, length = 200)
    var name: String = name
        protected set

    @Column(name="description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = price
        protected set

    @Embedded
    @AttributeOverride(name = "quantity", column = Column(name = "stock", nullable = false))
    var stock: Stock = stock
        protected set

    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(name = "likes_count", nullable = false)
    var likesCount: Int = 0
        protected set

    fun increaseLikesCount() {
        likesCount++
    }

    fun decreaseLikesCount() {
        if (likesCount > 0) likesCount--
    }

    fun decreaseStock(quantity: Quantity) {
        stock = stock.decrease(quantity.value)
    }

    fun hasEnoughStock(quantity: Quantity): Boolean {
        return stock.hasEnough(quantity.value)
    }

    fun validateStock(quantity: Quantity) {
        if (!hasEnoughStock(quantity)) {
            throw CoreException(
                ErrorType.INSUFFICIENT_STOCK,
                "재고가 부족합니다. 상품: $name (요청: ${quantity.value}, 재고: ${stock.quantity})"
            )
        }
    }

}
