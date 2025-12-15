package com.loopers.domain.order

interface OrderEventPublisher {
    fun publish(event: OrderEvent)
}
