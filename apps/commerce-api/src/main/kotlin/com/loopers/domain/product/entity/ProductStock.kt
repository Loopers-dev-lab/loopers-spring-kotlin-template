package com.loopers.domain.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.vo.ProductStockQuantity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stock")
class ProductStock protected constructor(
    productOptionId: Long,
    quantity: ProductStockQuantity,
) : BaseEntity() {
    @Column(name = "product_option_id", nullable = false)
    var productOptionId: Long = productOptionId
        protected set

    @Embedded
    var stockQuantity: ProductStockQuantity = quantity
        protected set

    fun validateEnoughQuantity(requested: Int) {
        if (stockQuantity.quantity < requested) {
            throw CoreException(ErrorType.PRODUCT_STOCK_NOT_ENOUGH, "재고가 부족합니다.")
        }
    }

    fun deduct(amount: Int) {
        validateEnoughQuantity(amount)
        stockQuantity = stockQuantity.decrease(amount)
    }

    companion object {
        fun create(productOptionId: Long, quantity: Int): ProductStock {
            return ProductStock(productOptionId, ProductStockQuantity(quantity))
        }
    }
}
