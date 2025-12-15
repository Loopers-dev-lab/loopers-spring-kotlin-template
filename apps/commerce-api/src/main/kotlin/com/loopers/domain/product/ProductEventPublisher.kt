package com.loopers.domain.product

interface ProductEventPublisher {
    fun publish(event: ProductEvent)
}
