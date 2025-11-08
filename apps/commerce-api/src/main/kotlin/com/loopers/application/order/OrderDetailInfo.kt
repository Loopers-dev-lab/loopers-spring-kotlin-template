package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import java.math.BigDecimal
import java.time.ZonedDateTime

data class OrderDetailInfo(
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val currency: String,
    val status: String,
    val items: List<OrderItemInfo>,
    val orderedAt: ZonedDateTime,
) {
    companion object {
        fun from(order: Order): OrderDetailInfo {
            val totalAmount = order.calculateTotalAmount()
            return OrderDetailInfo(
                orderId = order.id,
                userId = order.userId,
                totalAmount = totalAmount.amount,
                currency = totalAmount.currency.name,
                status = order.status.name,
                items = order.items.map { OrderItemInfo.from(it) },
                orderedAt = order.createdAt,
            )
        }
    }
}

data class OrderItemInfo(
    val productId: Long,
    val productName: String,
    val brandId: Long,
    val brandName: String,
    val quantity: Int,
    val priceAtOrder: BigDecimal,
    val currency: String,
) {
    companion object {
        fun from(orderItem: OrderItem): OrderItemInfo = OrderItemInfo(
            productId = orderItem.productId,
            productName = orderItem.productName,
            brandId = orderItem.brandId,
            brandName = orderItem.brandName,
            quantity = orderItem.quantity,
            priceAtOrder = orderItem.priceAtOrder.amount,
            currency = orderItem.priceAtOrder.currency.name,
        )
    }
}
