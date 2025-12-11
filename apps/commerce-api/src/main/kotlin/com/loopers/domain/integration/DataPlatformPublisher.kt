package com.loopers.domain.integration

import com.loopers.domain.order.OrderEvent
import com.loopers.domain.payment.PaymentEvent

interface DataPlatformPublisher {
    fun send(event: OrderEvent.OrderCreated)
    fun send(event: OrderEvent.OrderFailed)
    fun send(event: OrderEvent.OrderCompleted)
    fun send(event: PaymentEvent.PaymentSucceeded)
    fun send(event: PaymentEvent.PaymentFailed)
}
