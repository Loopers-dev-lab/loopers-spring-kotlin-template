package com.loopers.domain.payment

interface PaymentEventPublisher {
    fun publish(event: PaymentEvent.PaymentRequest)
    fun publish(event: PaymentEvent.PaymentHandled)
}
