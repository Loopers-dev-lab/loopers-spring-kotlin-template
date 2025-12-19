package com.loopers.interfaces.consumer.product.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderPaidEventPayload(
    val orderId: Long,
    val orderItems: List<OrderItem>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OrderItem(
        val productId: Long,
        val quantity: Int,
    )
}
