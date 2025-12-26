package com.loopers.support.outbox

import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
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
            assertThat(outbox.nextRetryAt).isNull()
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

        @DisplayName("from() 팩토리 메서드로 생성된 Outbox의 nextRetryAt은 null이다")
        @Test
        fun `from factory sets nextRetryAt to null`() {
            // given
            val envelope = CloudEventEnvelope(
                id = UUID.randomUUID().toString(),
                type = "loopers.order.created.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = Instant.now(),
                payload = """{"orderId": 123}""",
            )

            // when
            val outbox = Outbox.from(envelope)

            // then
            assertThat(outbox.nextRetryAt).isNull()
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

    @DisplayName("markForRetry() 메서드")
    @Nested
    inner class MarkForRetry {

        @DisplayName("nextRetryAt을 현재시간 + interval로 설정한다")
        @Test
        fun `sets nextRetryAt to now plus interval`() {
            // given
            val now = Instant.parse("2024-01-01T12:00:00Z")
            val interval = Duration.ofMinutes(5)
            val outbox = createOutbox()

            // when
            outbox.markForRetry(interval, now)

            // then
            assertThat(outbox.nextRetryAt).isEqualTo(now.plus(interval))
        }

        @DisplayName("여러 번 호출하면 마지막 호출 기준으로 nextRetryAt이 설정된다")
        @Test
        fun `updates nextRetryAt on subsequent calls`() {
            // given
            val firstNow = Instant.parse("2024-01-01T12:00:00Z")
            val secondNow = Instant.parse("2024-01-01T12:10:00Z")
            val interval = Duration.ofMinutes(5)
            val outbox = createOutbox()

            // when
            outbox.markForRetry(interval, firstNow)
            outbox.markForRetry(interval, secondNow)

            // then
            assertThat(outbox.nextRetryAt).isEqualTo(secondNow.plus(interval))
        }
    }

    @DisplayName("isExpired() 메서드")
    @Nested
    inner class IsExpired {

        @DisplayName("메시지가 maxAge보다 오래되면 true를 반환한다")
        @Test
        fun `returns true when message is older than maxAge`() {
            // given
            val createdAt = Instant.parse("2024-01-01T12:00:00Z")
            val now = Instant.parse("2024-01-01T13:00:00Z") // 1시간 후
            val maxAge = Duration.ofMinutes(30)
            val outbox = createOutbox(createdAt = createdAt)

            // when
            val result = outbox.isExpired(maxAge, now)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("메시지가 maxAge보다 젊으면 false를 반환한다")
        @Test
        fun `returns false when message is younger than maxAge`() {
            // given
            val createdAt = Instant.parse("2024-01-01T12:00:00Z")
            val now = Instant.parse("2024-01-01T12:15:00Z") // 15분 후
            val maxAge = Duration.ofMinutes(30)
            val outbox = createOutbox(createdAt = createdAt)

            // when
            val result = outbox.isExpired(maxAge, now)

            // then
            assertThat(result).isFalse()
        }

        @DisplayName("메시지가 정확히 maxAge와 같으면 false를 반환한다")
        @Test
        fun `returns false when message age equals maxAge`() {
            // given
            val createdAt = Instant.parse("2024-01-01T12:00:00Z")
            val now = Instant.parse("2024-01-01T12:30:00Z") // 정확히 30분 후
            val maxAge = Duration.ofMinutes(30)
            val outbox = createOutbox(createdAt = createdAt)

            // when
            val result = outbox.isExpired(maxAge, now)

            // then
            assertThat(result).isFalse()
        }
    }

    private fun createOutbox(
        id: Long = 0L,
        eventId: String = UUID.randomUUID().toString(),
        eventType: String = "loopers.order.created.v1",
        source: String = "commerce-api",
        aggregateType: String = "Order",
        aggregateId: String = "123",
        payload: String = """{"orderId": 123}""",
        createdAt: Instant = Instant.now(),
        nextRetryAt: Instant? = null,
    ): Outbox {
        return Outbox(
            id = id,
            eventId = eventId,
            eventType = eventType,
            source = source,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = payload,
            createdAt = createdAt,
            nextRetryAt = nextRetryAt,
        )
    }
}
