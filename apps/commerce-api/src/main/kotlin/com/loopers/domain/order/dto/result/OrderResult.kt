package com.loopers.domain.order.dto.result

import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.Order.Status
import com.loopers.domain.payment.dto.result.PaymentResult
import java.math.BigDecimal
import java.time.ZonedDateTime

class OrderResult {
    data class OrderDetail(
        val id: Long,
        val userId: Long,
        val originalPrice: BigDecimal,
        val finalPrice: BigDecimal,
        val status: Status,
        val createAt: ZonedDateTime,
        val updateAt: ZonedDateTime,
        val items: OrderItemResult.OrderItemDetails?,
        val payments: PaymentResult.PaymentDetails?,
    ) {
        data class OrderItemDetail(
            val id: Long,
            val orderId: Long,
            val productId: Long,
            val quantity: Int,
        )

        companion object {
            fun from(
                order: Order,
                orderItems: OrderItemResult.OrderItemDetails?,
                payments: PaymentResult.PaymentDetails?,
            ): OrderDetail {
                return OrderDetail(
                    order.id,
                    order.userId,
                    order.originalPrice.value,
                    order.finalPrice.value,
                    order.status,
                    order.createdAt,
                    order.updatedAt,
                    orderItems,
                    payments,
                )
            }

            fun from(order: Order, orderItems: OrderItemResult.OrderItemDetails?): OrderDetail {
                return OrderDetail(
                    order.id,
                    order.userId,
                    order.originalPrice.value,
                    order.finalPrice.value,
                    order.status,
                    order.createdAt,
                    order.updatedAt,
                    orderItems,
                    null,
                )
            }

            fun from(order: Order, payments: PaymentResult.PaymentDetails?): OrderDetail {
                return OrderDetail(
                    order.id,
                    order.userId,
                    order.originalPrice.value,
                    order.finalPrice.value,
                    order.status,
                    order.createdAt,
                    order.updatedAt,
                    null,
                    payments,
                )
            }
        }
    }

    data class OrderDetails(
        val orders: List<OrderDetail>,
    )
}
