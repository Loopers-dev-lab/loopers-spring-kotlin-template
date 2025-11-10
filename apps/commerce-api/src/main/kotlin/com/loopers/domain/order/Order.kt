package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_order")
class Order(
    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus,

    @Column(nullable = false, precision = 10, scale = 2)
    val totalAmount: BigDecimal,
) : BaseEntity() {

    init {
        validateTotalAmount(totalAmount)
    }

    companion object {
        fun of(userId: Long, totalAmount: BigDecimal): Order {
            return Order(
                userId = userId,
                status = OrderStatus.COMPLETED,
                totalAmount = totalAmount,
            )
        }
    }

    fun cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.")
        }
        this.status = OrderStatus.CANCELLED
    }

    fun isOwner(userId: Long): Boolean {
        return this.userId == userId
    }

    private fun validateTotalAmount(totalAmount: BigDecimal) {
        if (totalAmount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.")
        }
    }

    enum class OrderStatus(val description: String) {
        COMPLETED("완료"),
        CANCELLED("취소"),
    }
}
