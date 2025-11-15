package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Table(name = "order_items")
@Entity
class OrderItem(
    productId: Long,
    quantity: Int,
    productName: String,
    unitPrice: Money,
) : BaseEntity() {
    var productId: Long = productId
        private set

    var quantity: Int = quantity
        private set

    var productName: String = productName
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "unit_price", nullable = false))
    var unitPrice: Money = unitPrice
        private set

    init {
        if (quantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.")
        }

        if (unitPrice <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "단가는 0 보다 커야 합니다.")
        }
    }

    companion object {
        fun of(productId: Long, quantity: Int, productName: String, unitPrice: Money): OrderItem {
            return OrderItem(productId, quantity, productName, unitPrice)
        }

        fun create(productId: Long, quantity: Int, productName: String, unitPrice: Money): OrderItem {
            return OrderItem(productId, quantity, productName, unitPrice)
        }
    }
}
