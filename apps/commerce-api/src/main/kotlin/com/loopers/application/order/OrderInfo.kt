package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import org.springframework.data.domain.Page

data class OrderInfo(
    val id: Long,
    val memberId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val items: List<OrderItemInfo>,
    val createdAt: String,
) {
    companion object {
        fun from(order: Order): OrderInfo {
            return OrderInfo(
                id = order.id,
                memberId = order.memberId,
                status = order.status,
                totalAmount = order.totalAmount.amount,
                items = order.items.map { OrderItemInfo.from(it) },
                createdAt = order.createdAt.toString(),
            )
        }

        fun fromPage(page: Page<Order>): Page<OrderInfo> {
            return page.map { from(it) }
        }
    }
}

data class OrderItemInfo(
    val id: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val price: Long,
    val subtotal: Long,
) {
    companion object {
        fun from(orderItem: OrderItem): OrderItemInfo {
            return OrderItemInfo(
                id = orderItem.id,
                productId = orderItem.productId,
                productName = orderItem.productName,
                quantity = orderItem.quantity.value,
                price = orderItem.price.amount,
                subtotal = orderItem.subtotal.amount,
            )
        }
    }
}
