package com.loopers.interfaces.api.order

import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.dto.command.OrderItemCommand.Register.Item
import com.loopers.domain.order.dto.result.OrderItemResult
import com.loopers.domain.order.dto.result.OrderResult.OrderDetail
import com.loopers.domain.order.entity.Order.Status
import com.loopers.domain.payment.dto.result.PaymentResult
import java.math.BigDecimal
import java.time.ZonedDateTime

class OrderV1Dto {
    data class OrderResponse(
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
        companion object {
            fun from(orderDetail: OrderDetail?): OrderResponse? {
                return orderDetail
                    ?.let {
                        OrderResponse(
                            it.id,
                            it.userId,
                            it.originalPrice,
                            it.finalPrice,
                            it.status,
                            it.createAt,
                            it.updateAt,
                            it.items,
                            it.payments,
                        )
                    }
            }
        }
    }

    data class OrderRequest(
        val userId: Long,
        val originalPrice: BigDecimal,
        val finalPrice: BigDecimal,
        val items: List<Item>,
    ) {
        fun toRequestOrder(): OrderCommand.RequestOrder {
            return OrderCommand.RequestOrder.of(userId, originalPrice, finalPrice, items)
        }
    }
}
