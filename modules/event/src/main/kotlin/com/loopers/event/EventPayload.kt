package com.loopers.event

sealed interface EventPayload {
    val eventId: String
}

data class CatalogEventPayload(override val eventId: String, val productId: Long, val userId: Long, val type: CatalogType) :
    EventPayload

data class OrderEventPayload(override val eventId: String, val orderId: Long, val orderItems: List<OrderItemSnapshot>) :
    EventPayload

data class OrderItemSnapshot(val productId: Long, val quantity: Long)
