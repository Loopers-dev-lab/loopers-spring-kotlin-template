package com.loopers.domain.event

import java.time.ZonedDateTime
import java.util.UUID

/**
 * 재고 소진 이벤트
 * 상품의 재고가 0이 되었을 때 발행
 */
data class StockDepletedEvent(
    val eventId: UUID,
    val productId: Long,
    val previousQuantity: Int,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun create(productId: Long, previousQuantity: Int): StockDepletedEvent {
            return StockDepletedEvent(
                eventId = UUID.randomUUID(),
                productId = productId,
                previousQuantity = previousQuantity,
                createdAt = ZonedDateTime.now(),
            )
        }
    }
}

/**
 * 재고 부족 이벤트
 * 상품의 재고가 특정 임계값 이하로 떨어졌을 때 발행
 */
data class StockLowEvent(
    val eventId: UUID,
    val productId: Long,
    val currentQuantity: Int,
    val threshold: Int,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun create(productId: Long, currentQuantity: Int, threshold: Int): StockLowEvent {
            return StockLowEvent(
                eventId = UUID.randomUUID(),
                productId = productId,
                currentQuantity = currentQuantity,
                threshold = threshold,
                createdAt = ZonedDateTime.now(),
            )
        }
    }
}
