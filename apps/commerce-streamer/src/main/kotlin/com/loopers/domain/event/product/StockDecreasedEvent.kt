package com.loopers.domain.event.product

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 재고 차감 이벤트
 * - 주문 완료 시 재고 차감 후 발행
 * - catalog-events 토픽으로 발행 (key=productId)
 */
data class StockDecreasedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "STOCK_DECREASED",
    override val aggregateId: Long, // productId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val productId: Long,
    val orderId: Long,
    val quantity: Int, // 차감된 수량
    val remainingStock: Int, // 남은 재고
    val decreasedAt: Instant = Instant.now()
) : DomainEvent {
}
