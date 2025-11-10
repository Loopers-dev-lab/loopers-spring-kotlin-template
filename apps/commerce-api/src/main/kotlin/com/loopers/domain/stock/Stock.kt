package com.loopers.domain.stock

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_stock")
class Stock(
    @Column(unique = true, nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var quantity: Int,
) : BaseEntity() {

    init {
        validateQuantity(quantity)
    }

    companion object {
        fun of(productId: Long, quantity: Int = 0): Stock {
            return Stock(
                productId = productId,
                quantity = quantity,
            )
        }
    }

    fun increase(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "증가 수량은 0보다 커야 합니다.")
        }
        this.quantity += amount
    }

    fun deduct(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.")
        }
        validateQuantity(this.quantity - amount)
        this.quantity -= amount
    }

    fun isOutOfStock(): Boolean {
        return quantity == 0
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.")
        }
    }
}
