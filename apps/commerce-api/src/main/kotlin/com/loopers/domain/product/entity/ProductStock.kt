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
    stockQuantity: ProductStockQuantity,
) : BaseEntity() {
    @Column(name = "product_option_id", nullable = false)
    var productOptionId: Long = productOptionId
        protected set

    @Embedded
    var quantity: ProductStockQuantity = stockQuantity
        protected set

    fun validateDeduct(amount: Int) {
        if (quantity.value < amount) {
            throw CoreException(ErrorType.PRODUCT_STOCK_NOT_ENOUGH, "재고가 부족합니다.")
        }
    }

    fun deduct(amount: Int): Int {
        validateDeduct(amount)
        quantity = quantity.decrease(amount)
        return quantity.value
    }

    companion object {
        fun create(productOptionId: Long, quantity: Int): ProductStock {
            return ProductStock(productOptionId, ProductStockQuantity(quantity))
        }
    }
}
