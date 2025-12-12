package com.loopers.domain.order

interface OrderDataPlatformClient {
    fun sendOrderCompleted(orderId: Long): Boolean
}
