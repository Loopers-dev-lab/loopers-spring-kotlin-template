package com.loopers.support.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("Outbox 단위 테스트")
class OutboxTest {

    @DisplayName("from() 팩토리 메서드")
    @Nested
    inner class FromFactory {

        @DisplayName("CloudEventEnvelope로부터 Outbox를 생성한다")
        @Test
        fun `creates Outbox from CloudEventEnvelope`() {
            // given
            val eventId = UUID.randomUUID().toString()
            val eventType = "loopers.order.created.v1"
            val source = "commerce-api"
            val aggregateType = "Order"
            val aggregateId = "123"
            val time = Instant.parse("2024-01-01T12:00:00Z")
            val payload = """{"orderId": 123, "userId": 456}"""

            val envelope = CloudEventEnvelope(
                id = eventId,
                type = eventType,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )

            // when
            val outbox = Outbox.from(envelope)

            // then
            assertThat(outbox.id).isEqualTo(0L) // Not persisted yet
            assertThat(outbox.eventId).isEqualTo(eventId)
            assertThat(outbox.eventType).isEqualTo(eventType)
            assertThat(outbox.source).isEqualTo(source)
            assertThat(outbox.aggregateType).isEqualTo(aggregateType)
            assertThat(outbox.aggregateId).isEqualTo(aggregateId)
            assertThat(outbox.payload).isEqualTo(payload)
            assertThat(outbox.createdAt).isEqualTo(time)
        }

        @DisplayName("다른 이벤트 타입의 CloudEventEnvelope로부터 Outbox를 생성한다")
        @Test
        fun `creates Outbox from different event type CloudEventEnvelope`() {
            // given
            val eventId = UUID.randomUUID().toString()
            val eventType = "loopers.payment.paid.v1"
            val source = "commerce-api"
            val aggregateType = "Payment"
            val aggregateId = "789"
            val time = Instant.now()
            val payload = """{"paymentId": 789, "amount": 10000}"""

            val envelope = CloudEventEnvelope(
                id = eventId,
                type = eventType,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )

            // when
            val outbox = Outbox.from(envelope)

            // then
            assertThat(outbox.eventId).isEqualTo(eventId)
            assertThat(outbox.eventType).isEqualTo(eventType)
            assertThat(outbox.aggregateType).isEqualTo(aggregateType)
            assertThat(outbox.aggregateId).isEqualTo(aggregateId)
        }
    }

    @DisplayName("엔티티 필드 매핑")
    @Nested
    inner class FieldMapping {

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val eventId = "event-123"
            val eventType = "loopers.like.created.v1"
            val source = "commerce-api"
            val aggregateType = "Like"
            val aggregateId = "product-456"
            val time = Instant.parse("2024-06-15T10:30:00Z")
            val payload = """{"productId": 456, "userId": 1}"""

            val envelope = CloudEventEnvelope(
                id = eventId,
                type = eventType,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )

            // when
            val outbox = Outbox.from(envelope)

            // then
            assertThat(outbox.eventId).isEqualTo(eventId)
            assertThat(outbox.eventType).isEqualTo(eventType)
            assertThat(outbox.source).isEqualTo(source)
            assertThat(outbox.aggregateType).isEqualTo(aggregateType)
            assertThat(outbox.aggregateId).isEqualTo(aggregateId)
            assertThat(outbox.payload).isEqualTo(payload)
            assertThat(outbox.createdAt).isEqualTo(time)
        }
    }
}
