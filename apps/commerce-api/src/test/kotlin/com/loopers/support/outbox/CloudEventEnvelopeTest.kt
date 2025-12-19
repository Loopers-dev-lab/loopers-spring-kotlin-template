package com.loopers.support.outbox

import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("CloudEventEnvelope 단위 테스트")
class CloudEventEnvelopeTest {

    @DisplayName("인스턴스 생성")
    @Nested
    inner class Instantiation {

        @DisplayName("모든 필드를 가진 CloudEventEnvelope를 생성한다")
        @Test
        fun `create CloudEventEnvelope with all fields`() {
            // given
            val id = UUID.randomUUID().toString()
            val type = "loopers.order.created.v1"
            val source = "commerce-api"
            val aggregateType = "Order"
            val aggregateId = "123"
            val time = Instant.now()
            val payload = """{"orderId": 123, "userId": 456}"""

            // when
            val envelope = CloudEventEnvelope(
                id = id,
                type = type,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )

            // then
            assertThat(envelope.id).isEqualTo(id)
            assertThat(envelope.type).isEqualTo(type)
            assertThat(envelope.source).isEqualTo(source)
            assertThat(envelope.aggregateType).isEqualTo(aggregateType)
            assertThat(envelope.aggregateId).isEqualTo(aggregateId)
            assertThat(envelope.time).isEqualTo(time)
            assertThat(envelope.payload).isEqualTo(payload)
        }

        @DisplayName("빈 payload로 CloudEventEnvelope를 생성할 수 있다")
        @Test
        fun `create CloudEventEnvelope with empty payload`() {
            // given
            val id = UUID.randomUUID().toString()
            val time = Instant.now()

            // when
            val envelope = CloudEventEnvelope(
                id = id,
                type = "loopers.order.created.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = time,
                payload = "{}",
            )

            // then
            assertThat(envelope.payload).isEqualTo("{}")
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    inner class Equality {

        @DisplayName("같은 값을 가진 CloudEventEnvelope는 동등하다")
        @Test
        fun `CloudEventEnvelopes with same values are equal`() {
            // given
            val id = UUID.randomUUID().toString()
            val type = "loopers.order.created.v1"
            val source = "commerce-api"
            val aggregateType = "Order"
            val aggregateId = "123"
            val time = Instant.parse("2024-01-01T00:00:00Z")
            val payload = """{"orderId": 123}"""

            val envelope1 = CloudEventEnvelope(
                id = id,
                type = type,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )
            val envelope2 = CloudEventEnvelope(
                id = id,
                type = type,
                source = source,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                time = time,
                payload = payload,
            )

            // when & then
            assertThat(envelope1).isEqualTo(envelope2)
            assertThat(envelope1.hashCode()).isEqualTo(envelope2.hashCode())
        }

        @DisplayName("다른 id를 가진 CloudEventEnvelope는 동등하지 않다")
        @Test
        fun `CloudEventEnvelopes with different id are not equal`() {
            // given
            val time = Instant.parse("2024-01-01T00:00:00Z")
            val envelope1 = CloudEventEnvelope(
                id = UUID.randomUUID().toString(),
                type = "loopers.order.created.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = time,
                payload = """{"orderId": 123}""",
            )
            val envelope2 = CloudEventEnvelope(
                id = UUID.randomUUID().toString(),
                type = "loopers.order.created.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = time,
                payload = """{"orderId": 123}""",
            )

            // when & then
            assertThat(envelope1).isNotEqualTo(envelope2)
        }

        @DisplayName("다른 필드 값을 가진 CloudEventEnvelope는 동등하지 않다")
        @Test
        fun `CloudEventEnvelopes with different field values are not equal`() {
            // given
            val id = UUID.randomUUID().toString()
            val time = Instant.parse("2024-01-01T00:00:00Z")
            val envelope1 = CloudEventEnvelope(
                id = id,
                type = "loopers.order.created.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = time,
                payload = """{"orderId": 123}""",
            )
            val envelope2 = CloudEventEnvelope(
                id = id,
                type = "loopers.order.canceled.v1",
                source = "commerce-api",
                aggregateType = "Order",
                aggregateId = "123",
                time = time,
                payload = """{"orderId": 123}""",
            )

            // when & then
            assertThat(envelope1).isNotEqualTo(envelope2)
        }
    }
}
