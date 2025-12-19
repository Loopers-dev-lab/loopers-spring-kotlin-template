package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest
@DisplayName("OutboxFailedRdbRepository 통합 테스트")
class OutboxFailedRdbRepositoryIntegrationTest @Autowired constructor(
    private val outboxFailedRepository: OutboxFailedRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save()")
    @Nested
    inner class Save {

        @DisplayName("실패 레코드를 저장한다")
        @Test
        fun `persists failed record`() {
            // given
            val envelope = createCloudEventEnvelope()
            val outbox = Outbox.from(envelope)
            val failed = OutboxFailed.from(outbox, "Connection refused")

            // when
            val saved = outboxFailedRepository.save(failed)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.eventId).isEqualTo(outbox.eventId)
            assertThat(saved.eventType).isEqualTo(outbox.eventType)
            assertThat(saved.source).isEqualTo(outbox.source)
            assertThat(saved.aggregateType).isEqualTo(outbox.aggregateType)
            assertThat(saved.aggregateId).isEqualTo(outbox.aggregateId)
            assertThat(saved.payload).isEqualTo(outbox.payload)
            assertThat(saved.retryCount).isEqualTo(0)
            assertThat(saved.lastError).isEqualTo("Connection refused")
            assertThat(saved.failedAt).isNotNull()
            assertThat(saved.nextRetryAt).isNotNull()
        }

        @DisplayName("업데이트된 retryCount를 저장한다")
        @Test
        fun `persists updated retryCount`() {
            // given
            val envelope = createCloudEventEnvelope()
            val outbox = Outbox.from(envelope)
            val failed = OutboxFailed.from(outbox, "Initial error")
            val saved = outboxFailedRepository.save(failed)

            // when
            saved.incrementRetryCount("Retry error")
            val updated = outboxFailedRepository.save(saved)

            // then
            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.retryCount).isEqualTo(1)
            assertThat(updated.lastError).isEqualTo("Retry error")
        }
    }

    @DisplayName("saveAll()")
    @Nested
    inner class SaveAll {

        @DisplayName("여러 실패 레코드를 저장한다")
        @Test
        fun `persists multiple failed records`() {
            // given
            val failed1 = OutboxFailed.from(
                Outbox.from(createCloudEventEnvelope(aggregateId = "1")),
                "Error 1",
            )
            val failed2 = OutboxFailed.from(
                Outbox.from(createCloudEventEnvelope(aggregateId = "2")),
                "Error 2",
            )
            val failed3 = OutboxFailed.from(
                Outbox.from(createCloudEventEnvelope(aggregateId = "3")),
                "Error 3",
            )

            // when
            val savedList = outboxFailedRepository.saveAll(listOf(failed1, failed2, failed3))

            // then
            assertThat(savedList).hasSize(3)
            assertThat(savedList.map { it.id }).allMatch { it > 0L }
            assertThat(savedList.map { it.aggregateId }).containsExactly("1", "2", "3")
            assertThat(savedList.map { it.lastError }).containsExactly("Error 1", "Error 2", "Error 3")
        }

        @DisplayName("빈 리스트를 저장하면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when saving empty list`() {
            // when
            val savedList = outboxFailedRepository.saveAll(emptyList())

            // then
            assertThat(savedList).isEmpty()
        }
    }

    @DisplayName("findRetryable()")
    @Nested
    inner class FindRetryable {

        @DisplayName("nextRetryAt이 현재 시각 이전인 레코드를 반환한다")
        @Test
        fun `returns records where nextRetryAt is before now`() {
            // given - 재시도 가능한 레코드 (nextRetryAt이 과거)
            val retryable1 = createOutboxFailedWithNextRetryAt(
                aggregateId = "1",
                nextRetryAt = Instant.now().minusSeconds(60),
            )
            val retryable2 = createOutboxFailedWithNextRetryAt(
                aggregateId = "2",
                nextRetryAt = Instant.now().minusSeconds(30),
            )

            // given - 아직 재시도 불가능한 레코드 (nextRetryAt이 미래)
            val notYetRetryable = createOutboxFailedWithNextRetryAt(
                aggregateId = "3",
                nextRetryAt = Instant.now().plusSeconds(300),
            )

            outboxFailedRepository.save(retryable1)
            outboxFailedRepository.save(retryable2)
            outboxFailedRepository.save(notYetRetryable)

            // when
            val result = outboxFailedRepository.findRetryable(limit = 10)

            // then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.aggregateId }).containsExactlyInAnyOrder("1", "2")
        }

        @DisplayName("nextRetryAt 오름차순으로 정렬하여 반환한다")
        @Test
        fun `returns records ordered by nextRetryAt ascending`() {
            // given
            val oldest = createOutboxFailedWithNextRetryAt(
                aggregateId = "oldest",
                nextRetryAt = Instant.now().minusSeconds(120),
            )
            val middle = createOutboxFailedWithNextRetryAt(
                aggregateId = "middle",
                nextRetryAt = Instant.now().minusSeconds(60),
            )
            val newest = createOutboxFailedWithNextRetryAt(
                aggregateId = "newest",
                nextRetryAt = Instant.now().minusSeconds(10),
            )

            // 순서를 섞어서 저장
            outboxFailedRepository.save(middle)
            outboxFailedRepository.save(newest)
            outboxFailedRepository.save(oldest)

            // when
            val result = outboxFailedRepository.findRetryable(limit = 10)

            // then
            assertThat(result).hasSize(3)
            assertThat(result.map { it.aggregateId }).containsExactly("oldest", "middle", "newest")
        }

        @DisplayName("limit 개수만큼만 반환한다")
        @Test
        fun `returns limited number of records`() {
            // given
            (1..10).forEach { i ->
                outboxFailedRepository.save(
                    createOutboxFailedWithNextRetryAt(
                        aggregateId = i.toString(),
                        nextRetryAt = Instant.now().minusSeconds(i.toLong()),
                    ),
                )
            }

            // when
            val result = outboxFailedRepository.findRetryable(limit = 3)

            // then
            assertThat(result).hasSize(3)
        }

        @DisplayName("재시도 가능한 레코드가 없으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no retryable records`() {
            // given - 모든 레코드가 미래 시점
            outboxFailedRepository.save(
                createOutboxFailedWithNextRetryAt(
                    aggregateId = "1",
                    nextRetryAt = Instant.now().plusSeconds(300),
                ),
            )

            // when
            val result = outboxFailedRepository.findRetryable(limit = 10)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("레코드가 없으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no records exist`() {
            // when
            val result = outboxFailedRepository.findRetryable(limit = 10)

            // then
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("delete()")
    @Nested
    inner class Delete {

        @DisplayName("레코드를 삭제한다")
        @Test
        fun `removes record`() {
            // given
            val envelope = createCloudEventEnvelope()
            val outbox = Outbox.from(envelope)
            val failed = OutboxFailed.from(outbox, "Error")
            val saved = outboxFailedRepository.save(failed)

            // when
            outboxFailedRepository.delete(saved)

            // then
            val result = outboxFailedRepository.findRetryable(limit = 100)
            assertThat(result).isEmpty()
        }

        @DisplayName("여러 레코드 중 특정 레코드만 삭제한다")
        @Test
        fun `removes only specific record from multiple records`() {
            // given
            val failed1 = outboxFailedRepository.save(
                OutboxFailed.from(
                    Outbox.from(createCloudEventEnvelope(aggregateId = "1")),
                    "Error 1",
                ),
            )
            val failed2 = outboxFailedRepository.save(
                OutboxFailed.from(
                    Outbox.from(createCloudEventEnvelope(aggregateId = "2")),
                    "Error 2",
                ),
            )
            val failed3 = outboxFailedRepository.save(
                OutboxFailed.from(
                    Outbox.from(createCloudEventEnvelope(aggregateId = "3")),
                    "Error 3",
                ),
            )

            // when - 두 번째 레코드 삭제
            outboxFailedRepository.delete(failed2)

            // then - 과거 시점으로 설정하여 조회 (findRetryable 사용)
            // 기본 nextRetryAt이 현재+1초이므로, 잠시 대기 후 조회
            Thread.sleep(1100)
            val remaining = outboxFailedRepository.findRetryable(limit = 100)
            assertThat(remaining).hasSize(2)
            assertThat(remaining.map { it.id }).containsExactlyInAnyOrder(failed1.id, failed3.id)
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

    private fun createOutboxFailedWithNextRetryAt(
        eventId: String = UUID.randomUUID().toString(),
        aggregateId: String = "123",
        nextRetryAt: Instant,
    ): OutboxFailed {
        return OutboxFailed(
            eventId = eventId,
            eventType = "loopers.order.created.v1",
            source = "commerce-api",
            aggregateType = "Order",
            aggregateId = aggregateId,
            payload = """{"orderId": "$aggregateId"}""",
            retryCount = 0,
            lastError = "Test error",
            failedAt = Instant.now(),
            nextRetryAt = nextRetryAt,
        )
    }
}
