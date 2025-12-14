package com.loopers.infrastructure.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.IntegrationTest
import com.loopers.domain.outbox.AggregateType
import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@Disabled("카프카 발행 테스트 - 로컬 1회 테스트")
@DisplayName("OutboxEventPublisherImpl 통합 테스트")
class OutboxEventPublisherImplTest : IntegrationTest() {

    @Autowired
    private lateinit var outboxEventPublisher: OutboxEventPublisherImpl

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("LikeCountChanged 이벤트를 성공적으로 발행한다")
    fun `publish LikeCountChanged event successfully`() {
        // given
        val event = OutboxEvent.LikeCountChanged(
            productId = 1L,
            userId = 100L,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
            timestamp = LocalDateTime.now(),
        )

        val outbox = Outbox.create(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
        )

        // when
        val result = outboxEventPublisher.publish(outbox)

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("ViewCountIncreased 이벤트를 성공적으로 발행한다")
    fun `publish ViewCountIncreased event successfully`() {
        // given
        val event = OutboxEvent.ViewCountIncreased(
            productId = 1L,
            userId = 100L,
            timestamp = LocalDateTime.now(),
        )

        val outbox = Outbox.create(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.ViewCountIncreased.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
        )

        // when
        val result = outboxEventPublisher.publish(outbox)

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("SoldOut 이벤트를 성공적으로 발행한다")
    fun `publish SoldOut event successfully`() {
        // given
        val event = OutboxEvent.SoldOut(
            productId = 1L,
        )

        val outbox = Outbox.create(
            aggregateType = AggregateType.PRODUCT,
            aggregateId = event.productId.toString(),
            eventType = OutboxEvent.SoldOut.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
        )

        // when
        val result = outboxEventPublisher.publish(outbox)

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("OrderCompleted 이벤트를 성공적으로 발행한다")
    fun `publish OrderCompleted event successfully`() {
        // given
        val event = OutboxEvent.OrderCompleted(
            orderId = 1L,
            userId = 100L,
            totalAmount = 50000L,
            items = listOf(
                OutboxEvent.OrderCompleted.OrderItem(
                    productId = 1L,
                    quantity = 2,
                ),
                OutboxEvent.OrderCompleted.OrderItem(
                    productId = 2L,
                    quantity = 1,
                ),
            ),
            timestamp = LocalDateTime.now(),
        )

        val outbox = Outbox.create(
            aggregateType = AggregateType.ORDER,
            aggregateId = event.orderId.toString(),
            eventType = OutboxEvent.OrderCompleted.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
        )

        // when
        val result = outboxEventPublisher.publish(outbox)

        // then
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("OrderCanceled 이벤트를 성공적으로 발행한다")
    fun `publish OrderCanceled event successfully`() {
        // given
        val event = OutboxEvent.OrderCanceled(
            orderId = 1L,
            userId = 100L,
            reason = "고객 요청",
            items = listOf(
                OutboxEvent.OrderCanceled.OrderItem(
                    productId = 1L,
                    quantity = 2,
                ),
            ),
            timestamp = LocalDateTime.now(),
        )

        val outbox = Outbox.create(
            aggregateType = AggregateType.ORDER,
            aggregateId = event.orderId.toString(),
            eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
            payload = objectMapper.writeValueAsString(event),
        )

        // when
        val result = outboxEventPublisher.publish(outbox)

        // then
        assertThat(result).isTrue()
    }
}
