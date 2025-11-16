package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "stock")
class Stock(

    @Column(nullable = false)
    var quantity: Long,

    @Column(name = "ref_product_id", nullable = false)
    val productId: Long,

    ) : BaseEntity() {

    companion object {
        fun create(quantity: Long, productId: Long): Stock {
            return Stock(
                quantity = quantity,
                productId = productId,
            )
        }
    }

    fun decrease(quantity: Long) {
        require(quantity > 0) { "차감 수량은 0보다 커야 합니다." }

        if (isInsufficientStock(quantity)) {
            throw CoreException(ErrorType.INSUFFICIENT_STOCK)
        }

        this.quantity -= quantity
    }

    private fun isInsufficientStock(quantity: Long): Boolean {
        return this.quantity < quantity
    }
}
