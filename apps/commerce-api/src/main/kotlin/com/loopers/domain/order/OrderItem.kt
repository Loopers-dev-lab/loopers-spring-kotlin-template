package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_order_item")
class OrderItem(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false, length = 200)
    val productName: String,

    @Column(nullable = false, length = 100)
    val brandName: String,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val totalPrice: BigDecimal,
) : BaseEntity() {

    init {
        validateQuantity(quantity)
        validatePrice(price)
        validateTotalPrice(totalPrice)
    }

    companion object {
        fun of(
            orderId: Long,
            productId: Long,
            productName: String,
            brandName: String,
            price: BigDecimal,
            quantity: Int,
        ): OrderItem {
            return OrderItem(
                orderId = orderId,
                productId = productId,
                productName = productName,
                brandName = brandName,
                price = price,
                quantity = quantity,
                totalPrice = calculateTotalPrice(price, quantity),
            )
        }

        private fun calculateTotalPrice(price: BigDecimal, quantity: Int): BigDecimal {
            return price.multiply(BigDecimal(quantity))
        }
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        }
    }

    private fun validatePrice(price: BigDecimal) {
        if (price < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.")
        }
    }

    private fun validateTotalPrice(totalPrice: BigDecimal) {
        if (totalPrice < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "총 금액은 0 이상이어야 합니다.")
        }
    }
}
