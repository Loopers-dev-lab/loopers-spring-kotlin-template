package com.loopers.domain.outbox

interface OutboxEventPublisher {
    fun publish(outbox: Outbox): Boolean
}
