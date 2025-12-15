package com.loopers.domain.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * OutboxEvent 도메인 로직 단위 테스트
 */
class OutboxEventTest {

    @Test
    fun `OutboxEvent를 PENDING 상태로 생성할 수 있다`() {
        // when
        val outboxEvent = OutboxEvent.create(
            eventType = "LikeAddedEvent",
            topic = "catalog-events",
            partitionKey = "100",
            payload = """{"userId":1,"productId":100}""",
            aggregateType = "Product",
            aggregateId = 100L,
        )

        // then
        assertThat(outboxEvent.eventType).isEqualTo("LikeAddedEvent")
        assertThat(outboxEvent.topic).isEqualTo("catalog-events")
        assertThat(outboxEvent.partitionKey).isEqualTo("100")
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.PENDING)
        assertThat(outboxEvent.retryCount).isEqualTo(0)
        assertThat(outboxEvent.publishedAt).isNull()
    }

    @Test
    fun `OutboxEvent를 PUBLISHED 상태로 변경할 수 있다`() {
        // given
        val outboxEvent = createOutboxEvent()

        // when
        outboxEvent.markAsPublished()

        // then
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.PUBLISHED)
        assertThat(outboxEvent.publishedAt).isNotNull
    }

    @Test
    fun `OutboxEvent가 실패하면 재시도 횟수가 증가하고 에러 메시지가 저장된다`() {
        // given
        val outboxEvent = createOutboxEvent()
        val maxRetryCount = 3

        // when
        outboxEvent.markAsFailed("Kafka 전송 실패", maxRetryCount)

        // then
        // 첫 번째 실패 시에는 PENDING 상태 유지 (재시도 가능)
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.PENDING)
        assertThat(outboxEvent.retryCount).isEqualTo(1)
        assertThat(outboxEvent.errorMessage).isEqualTo("Kafka 전송 실패")
    }

    @Test
    fun `재시도 가능 여부를 확인할 수 있다`() {
        // given
        val outboxEvent = createOutboxEvent()
        val maxRetryCount = 3

        // when & then
        assertThat(outboxEvent.canRetry(maxRetryCount = maxRetryCount)).isTrue

        outboxEvent.markAsFailed("실패 1", maxRetryCount)
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.PENDING)
        assertThat(outboxEvent.canRetry(maxRetryCount = maxRetryCount)).isTrue

        outboxEvent.markAsFailed("실패 2", maxRetryCount)
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.PENDING)
        assertThat(outboxEvent.canRetry(maxRetryCount = maxRetryCount)).isTrue

        outboxEvent.markAsFailed("실패 3", maxRetryCount)
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.FAILED)
        assertThat(outboxEvent.canRetry(maxRetryCount = maxRetryCount)).isFalse
    }

    @Test
    fun `재시도 횟수가 최대값을 초과하면 재시도할 수 없다`() {
        // given
        val outboxEvent = createOutboxEvent()
        val maxRetryCount = 3
        outboxEvent.markAsFailed("실패 1", maxRetryCount)
        outboxEvent.markAsFailed("실패 2", maxRetryCount)
        outboxEvent.markAsFailed("실패 3", maxRetryCount)
        outboxEvent.markAsFailed("실패 4", maxRetryCount)

        // when & then
        assertThat(outboxEvent.canRetry(maxRetryCount = maxRetryCount)).isFalse
        assertThat(outboxEvent.status).isEqualTo(OutboxEventStatus.FAILED)
        assertThat(outboxEvent.retryCount).isEqualTo(4)
    }

    private fun createOutboxEvent(): OutboxEvent {
        return OutboxEvent.create(
            eventType = "LikeAddedEvent",
            topic = "catalog-events",
            partitionKey = "100",
            payload = """{"userId":1,"productId":100}""",
            aggregateType = "Product",
            aggregateId = 100L,
        )
    }
}
