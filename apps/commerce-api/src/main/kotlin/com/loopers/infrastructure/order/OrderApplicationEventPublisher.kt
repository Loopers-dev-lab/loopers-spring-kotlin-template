package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class OrderApplicationEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : OrderEventPublisher {
    override fun publish(event: OrderEvent) = applicationEventPublisher.publishEvent(event)
}
