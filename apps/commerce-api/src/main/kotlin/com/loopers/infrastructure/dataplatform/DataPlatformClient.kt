package com.loopers.infrastructure.dataplatform

import com.loopers.domain.order.OrderSuccessEvent
import java.time.LocalDateTime

interface DataPlatformClient {
    fun sendOrderEvent(payload: OrderEventPayload)
}

data class OrderEventPayload(
    val orderId: Long,
    val userId: Long,
    val couponId: Long?,
    val occurredAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun from(event: OrderSuccessEvent) = OrderEventPayload(event.orderId, event.userId, event.couponId)
    }
}
