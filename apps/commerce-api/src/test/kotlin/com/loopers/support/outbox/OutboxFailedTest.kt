package com.loopers.support.outbox

import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("OutboxFailed 단위 테스트")
class OutboxFailedTest {

    @DisplayName("from() 팩토리 메서드")
    @Nested
    inner class FromFactory {

        @DisplayName("Outbox와 에러 메시지로부터 OutboxFailed를 생성한다")
        @Test
        fun `creates OutboxFailed from Outbox and error`() {
            // given
            val envelope = createCloudEventEnvelope()
            val outbox = Outbox.from(envelope)
            val error = "Connection timeout"
            val beforeCreate = Instant.now()

            // when
            val failed = OutboxFailed.from(outbox, error)
            val afterCreate = Instant.now()

            // then
            assertThat(failed.id).isEqualTo(0L) // Not persisted yet
            assertThat(failed.eventId).isEqualTo(outbox.eventId)
            assertThat(failed.eventType).isEqualTo(outbox.eventType)
            assertThat(failed.source).isEqualTo(outbox.source)
            assertThat(failed.aggregateType).isEqualTo(outbox.aggregateType)
            assertThat(failed.aggregateId).isEqualTo(outbox.aggregateId)
            assertThat(failed.payload).isEqualTo(outbox.payload)
            assertThat(failed.errorMessage).isEqualTo(error)
            assertThat(failed.failedAt).isAfterOrEqualTo(beforeCreate)
            assertThat(failed.failedAt).isBeforeOrEqualTo(afterCreate)
        }

        @DisplayName("다른 이벤트 타입의 Outbox로부터 OutboxFailed를 생성한다")
        @Test
        fun `creates OutboxFailed from different event type Outbox`() {
            // given
            val envelope = createCloudEventEnvelope(
                type = "loopers.payment.failed.v1",
                aggregateType = "Payment",
                aggregateId = "789",
            )
            val outbox = Outbox.from(envelope)
            val error = "Kafka broker unavailable"

            // when
            val failed = OutboxFailed.from(outbox, error)

            // then
            assertThat(failed.eventType).isEqualTo("loopers.payment.failed.v1")
            assertThat(failed.aggregateType).isEqualTo("Payment")
            assertThat(failed.aggregateId).isEqualTo("789")
            assertThat(failed.errorMessage).isEqualTo(error)
        }
    }

    // ===========================================
    // 헬퍼 메서드
    // ===========================================

    private fun createCloudEventEnvelope(
        id: String = UUID.randomUUID().toString(),
        type: String = "loopers.order.created.v1",
        source: String = "commerce-api",
        aggregateType: String = "Order",
        aggregateId: String = "123",
        time: Instant = Instant.now(),
        payload: String = """{"orderId": $aggregateId}""",
    ): CloudEventEnvelope {
        return CloudEventEnvelope(
            id = id,
            type = type,
            source = source,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            time = time,
            payload = payload,
        )
    }

    private fun createOutboxFailed(
        eventId: String = UUID.randomUUID().toString(),
        eventType: String = "loopers.order.created.v1",
        source: String = "commerce-api",
        aggregateType: String = "Order",
        aggregateId: String = "123",
        payload: String = """{"orderId": $aggregateId}""",
        errorMessage: String = "Initial error",
        failedAt: Instant = Instant.now(),
    ): OutboxFailed {
        return OutboxFailed(
            eventId = eventId,
            eventType = eventType,
            source = source,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = payload,
            errorMessage = errorMessage,
            failedAt = failedAt,
        )
    }
}
