package com.loopers.domain.integration

import com.loopers.application.order.event.OrderEvent
import com.loopers.application.payment.event.PaymentEvent

interface DataPlatformPublisher {
    fun send(event: OrderEvent.OrderCreated)
    fun send(event: OrderEvent.OrderFailed)
    fun send(event: OrderEvent.OrderCompleted)
    fun send(event: PaymentEvent.PaymentSucceeded)
    fun send(event: PaymentEvent.PaymentFailed)
}
