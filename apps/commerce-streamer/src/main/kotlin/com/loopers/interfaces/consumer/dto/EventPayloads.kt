package com.loopers.interfaces.consumer.dto

import java.time.Instant

/** CloudEventEnvelope 역직렬화용 */
data class CloudEventEnvelope(
    val id: String,
    val type: String,
    val source: String,
    val aggregateType: String,
    val aggregateId: String,
    val time: Instant,
    val payload: String,
)

/** LikeCreatedEventV1 페이로드 */
data class LikeCreatedEventPayload(
    val userId: Long,
    val productId: Long,
    val occurredAt: Instant,
)

/** LikeCanceledEventV1 페이로드 */
data class LikeCanceledEventPayload(
    val userId: Long,
    val productId: Long,
    val occurredAt: Instant,
)

/** OrderPaidEventV1 페이로드 */
data class OrderPaidEventPayload(
    val orderId: Long,
    val userId: Long,
    val totalAmount: Long,
    val orderItems: List<OrderItemPayload>,
    val occurredAt: Instant,
) {
    data class OrderItemPayload(
        val productId: Long,
        val quantity: Int,
    )
}

/** ProductViewedEventV1 페이로드 */
data class ProductViewedEventPayload(
    val productId: Long,
    val userId: Long,
    val occurredAt: Instant,
)

/** StockDepletedEventV1 페이로드 */
data class StockDepletedEventPayload(
    val productId: Long,
    val occurredAt: Instant,
)
