package com.loopers.application.order

import com.loopers.domain.order.Order
import java.math.BigDecimal

data class OrderCreateInfo(
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val currency: String,
    val status: String,
) {
    companion object {
        fun from(order: Order): OrderCreateInfo {
            val totalAmount = order.calculateTotalAmount()
            return OrderCreateInfo(
                orderId = order.id,
                userId = order.userId,
                totalAmount = totalAmount.amount,
                currency = totalAmount.currency.name,
                status = order.status.name,
            )
        }
    }
}
