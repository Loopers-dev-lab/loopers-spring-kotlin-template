package com.loopers.interfaces.consumer.product.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class LikeEventPayload(
    val productId: Long,
    val userId: Long,
)
