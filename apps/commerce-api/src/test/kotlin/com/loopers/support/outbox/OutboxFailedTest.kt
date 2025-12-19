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
            assertThat(failed.retryCount).isEqualTo(0)
            assertThat(failed.lastError).isEqualTo(error)
            assertThat(failed.failedAt).isAfterOrEqualTo(beforeCreate)
            assertThat(failed.failedAt).isBeforeOrEqualTo(afterCreate)
            // nextRetryAt should be 1 second after failedAt (2^0 = 1)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeCreate.plusSeconds(1))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterCreate.plusSeconds(1))
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
            assertThat(failed.lastError).isEqualTo(error)
        }
    }

    @DisplayName("incrementRetryCount() 메서드")
    @Nested
    inner class IncrementRetryCount {

        @DisplayName("retryCount를 증가시키고 lastError를 업데이트한다")
        @Test
        fun `increases retryCount and updates lastError`() {
            // given
            val failed = createOutboxFailed()
            val initialRetryCount = failed.retryCount
            val newError = "Network error"

            // when
            failed.incrementRetryCount(newError)

            // then
            assertThat(failed.retryCount).isEqualTo(initialRetryCount + 1)
            assertThat(failed.lastError).isEqualTo(newError)
        }

        @DisplayName("여러 번 호출하면 retryCount가 누적된다")
        @Test
        fun `accumulates retryCount on multiple calls`() {
            // given
            val failed = createOutboxFailed()

            // when
            failed.incrementRetryCount("Error 1")
            failed.incrementRetryCount("Error 2")
            failed.incrementRetryCount("Error 3")

            // then
            assertThat(failed.retryCount).isEqualTo(3)
            assertThat(failed.lastError).isEqualTo("Error 3")
        }
    }

    @DisplayName("지수 백오프 계산")
    @Nested
    inner class ExponentialBackoff {

        @DisplayName("첫 번째 재시도는 2초 후로 설정된다 (2^1)")
        @Test
        fun `first retry is scheduled after 2 seconds`() {
            // given
            val failed = createOutboxFailed()
            val beforeIncrement = Instant.now()

            // when
            failed.incrementRetryCount("Error")
            val afterIncrement = Instant.now()

            // then - retryCount is now 1, so backoff is 2^1 = 2 seconds
            assertThat(failed.retryCount).isEqualTo(1)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeIncrement.plusSeconds(2))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterIncrement.plusSeconds(2).plusMillis(100))
        }

        @DisplayName("두 번째 재시도는 4초 후로 설정된다 (2^2)")
        @Test
        fun `second retry is scheduled after 4 seconds`() {
            // given
            val failed = createOutboxFailed()

            // when
            failed.incrementRetryCount("Error 1") // retryCount = 1
            val beforeSecondIncrement = Instant.now()
            failed.incrementRetryCount("Error 2") // retryCount = 2
            val afterSecondIncrement = Instant.now()

            // then - retryCount is now 2, so backoff is 2^2 = 4 seconds
            assertThat(failed.retryCount).isEqualTo(2)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeSecondIncrement.plusSeconds(4))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterSecondIncrement.plusSeconds(4).plusMillis(100))
        }

        @DisplayName("세 번째 재시도는 8초 후로 설정된다 (2^3)")
        @Test
        fun `third retry is scheduled after 8 seconds`() {
            // given
            val failed = createOutboxFailed()

            // when
            failed.incrementRetryCount("Error 1") // retryCount = 1
            failed.incrementRetryCount("Error 2") // retryCount = 2
            val beforeThirdIncrement = Instant.now()
            failed.incrementRetryCount("Error 3") // retryCount = 3
            val afterThirdIncrement = Instant.now()

            // then - retryCount is now 3, so backoff is 2^3 = 8 seconds
            assertThat(failed.retryCount).isEqualTo(3)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeThirdIncrement.plusSeconds(8))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterThirdIncrement.plusSeconds(8).plusMillis(100))
        }

        @DisplayName("백오프는 최대 5분(300초)으로 제한된다")
        @Test
        fun `backoff is capped at 5 minutes`() {
            // given
            val failed = createOutboxFailed()

            // when - 9번 재시도 (2^9 = 512초 > 300초)
            repeat(9) {
                failed.incrementRetryCount("Error $it")
            }
            val beforeNinthIncrement = Instant.now()
            failed.incrementRetryCount("Error 9") // retryCount = 10
            val afterNinthIncrement = Instant.now()

            // then - retryCount is now 10, but backoff is capped at 300 seconds
            assertThat(failed.retryCount).isEqualTo(10)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeNinthIncrement.plusSeconds(300))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterNinthIncrement.plusSeconds(300).plusMillis(100))
        }

        @DisplayName("매우 많은 재시도에도 5분 제한이 유지된다")
        @Test
        fun `5 minute cap is maintained for many retries`() {
            // given
            val failed = createOutboxFailed()

            // when - 20번 재시도
            repeat(20) {
                failed.incrementRetryCount("Error $it")
            }
            val beforeIncrement = Instant.now()
            failed.incrementRetryCount("Final error") // retryCount = 21
            val afterIncrement = Instant.now()

            // then - retryCount is 21, but backoff is still capped at 300 seconds
            assertThat(failed.retryCount).isEqualTo(21)
            assertThat(failed.nextRetryAt).isAfterOrEqualTo(beforeIncrement.plusSeconds(300))
            assertThat(failed.nextRetryAt).isBeforeOrEqualTo(afterIncrement.plusSeconds(300).plusMillis(100))
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
        retryCount: Int = 0,
        lastError: String = "Initial error",
        failedAt: Instant = Instant.now(),
        nextRetryAt: Instant = Instant.now().plusSeconds(1),
    ): OutboxFailed {
        return OutboxFailed(
            eventId = eventId,
            eventType = eventType,
            source = source,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = payload,
            retryCount = retryCount,
            lastError = lastError,
            failedAt = failedAt,
            nextRetryAt = nextRetryAt,
        )
    }
}
