package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import java.time.ZonedDateTime

object OrderResult {

    data class ListInfo(
        val id: Long,
        val totalAmount: Long,
        val status: OrderStatus,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(
                order: Order,
            ): ListInfo {
                return ListInfo(
                    id = order.id,
                    totalAmount = order.totalAmount,
                    status = order.status,
                    orderedAt = order.createdAt,
                )
            }
        }
    }
}
