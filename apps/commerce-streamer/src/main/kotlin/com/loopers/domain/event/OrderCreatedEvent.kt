package com.loopers.domain.event

import java.time.ZonedDateTime
import java.util.UUID

/**
 * 주문 관련 이벤트
 */

/**
 * 주문 생성 이벤트
 * 주문이 생성되었을 때 발행
 */
data class OrderCreatedEvent(
    val eventId: UUID,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val couponId: Long?,
    val items: List<OrderItemInfo>,
    val createdAt: ZonedDateTime,
) {
    /**
     * 주문 상품 정보
     */
    data class OrderItemInfo(
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val priceAtOrder: Long,
    )
}
