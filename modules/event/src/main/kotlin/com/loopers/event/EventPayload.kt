package com.loopers.event

sealed interface EventPayload

data class CatalogEventPayload(val productId: Long, val userId: Long) : EventPayload

data class OrderEventPayload(val productId: Long, val orderId: Long) : EventPayload

data class LikeEventPayload(val eventId: String, val productId: Long, val userId: Long, val type: LikeType) : EventPayload {
    enum class LikeType {
        LIKED,
        UNLIKED,
    }
}
