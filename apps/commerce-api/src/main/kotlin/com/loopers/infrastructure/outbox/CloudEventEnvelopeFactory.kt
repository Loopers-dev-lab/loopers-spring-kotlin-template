package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.support.event.DomainEvent
import com.loopers.support.outbox.CloudEventEnvelope
import com.loopers.support.outbox.EventTypeResolver
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CloudEventEnvelopeFactory(
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val SOURCE = "commerce-api"
    }

    fun create(
        event: DomainEvent,
        aggregateType: String,
        aggregateId: String,
    ): CloudEventEnvelope {
        return CloudEventEnvelope(
            id = UUID.randomUUID().toString(),
            type = EventTypeResolver.resolve(event),
            source = SOURCE,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            time = event.occurredAt,
            payload = objectMapper.writeValueAsString(event),
        )
    }
}
