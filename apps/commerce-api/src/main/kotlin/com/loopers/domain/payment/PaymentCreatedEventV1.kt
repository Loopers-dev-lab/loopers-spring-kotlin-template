package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class PaymentCreatedEventV1(
    val paymentId: Long,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
