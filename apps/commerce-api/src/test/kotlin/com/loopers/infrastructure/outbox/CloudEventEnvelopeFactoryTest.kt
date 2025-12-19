package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.order.OrderCreatedEventV1
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
@DisplayName("CloudEventEnvelopeFactory 단위 테스트")
class CloudEventEnvelopeFactoryTest @Autowired constructor(
    private val cloudEventEnvelopeFactory: CloudEventEnvelopeFactory,
    private val objectMapper: ObjectMapper,
) {

    @DisplayName("create 메서드")
    @Nested
    inner class Create {

        @DisplayName("UUID 형식의 id를 생성한다")
        @Test
        fun `creates envelope with UUID id`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )
            val aggregateType = "Order"
            val aggregateId = "1"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            assertThat(envelope.id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }

        @DisplayName("EventTypeResolver를 통해 type을 설정한다")
        @Test
        fun `sets type from EventTypeResolver`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )
            val aggregateType = "Order"
            val aggregateId = "1"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            assertThat(envelope.type).isEqualTo("loopers.order.created.v1")
        }

        @DisplayName("source를 'commerce-api'로 설정한다")
        @Test
        fun `sets source as commerce-api`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )
            val aggregateType = "Order"
            val aggregateId = "1"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            assertThat(envelope.source).isEqualTo("commerce-api")
        }

        @DisplayName("파라미터로 전달된 aggregateType과 aggregateId를 설정한다")
        @Test
        fun `sets aggregateType and aggregateId from parameters`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )
            val aggregateType = "Order"
            val aggregateId = "123"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            assertThat(envelope.aggregateType).isEqualTo(aggregateType)
            assertThat(envelope.aggregateId).isEqualTo(aggregateId)
        }

        @DisplayName("event.occurredAt으로 time을 설정한다")
        @Test
        fun `sets time from event occurredAt`() {
            // given
            val occurredAt = Instant.parse("2024-01-01T12:00:00Z")
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
                occurredAt = occurredAt,
            )
            val aggregateType = "Order"
            val aggregateId = "1"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            assertThat(envelope.time).isEqualTo(occurredAt)
        }

        @DisplayName("이벤트를 JSON으로 직렬화하여 payload에 저장한다")
        @Test
        fun `serializes event to JSON payload`() {
            // given
            val occurredAt = Instant.parse("2024-01-01T12:00:00Z")
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
                occurredAt = occurredAt,
            )
            val aggregateType = "Order"
            val aggregateId = "1"

            // when
            val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)

            // then
            val deserializedEvent = objectMapper.readValue<OrderCreatedEventV1>(envelope.payload)
            assertThat(deserializedEvent.orderId).isEqualTo(1L)
            assertThat(deserializedEvent.orderItems).hasSize(1)
            assertThat(deserializedEvent.orderItems[0].productId).isEqualTo(100L)
            assertThat(deserializedEvent.orderItems[0].quantity).isEqualTo(2)
            assertThat(deserializedEvent.occurredAt).isEqualTo(occurredAt)
        }
    }
}
