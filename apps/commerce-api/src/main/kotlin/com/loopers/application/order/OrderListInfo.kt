package com.loopers.application.order

import com.loopers.domain.order.Order
import java.math.BigDecimal
import java.time.ZonedDateTime

data class OrderListInfo(
    val orderId: Long,
    val totalAmount: BigDecimal,
    val currency: String,
    val status: String,
    val orderedAt: ZonedDateTime
) {
    companion object {
        fun from(order: Order): OrderListInfo {
            val totalAmount = order.calculateTotalAmount()
            return OrderListInfo(
                orderId = order.id,
                totalAmount = totalAmount.amount,
                currency = totalAmount.currency.name,
                status = order.status.name,
                orderedAt = order.createdAt
            )
        }
    }
}
