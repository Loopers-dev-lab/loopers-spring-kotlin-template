package com.loopers.interfaces.consumer.product.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StockDepletedEventPayload(
    val productId: Long,
)
