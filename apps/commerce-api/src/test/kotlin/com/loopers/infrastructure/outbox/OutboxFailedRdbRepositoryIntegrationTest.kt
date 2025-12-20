package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
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
            assertThat(saved.errorMessage).isEqualTo("Connection refused")
            assertThat(saved.failedAt).isNotNull()
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
            assertThat(savedList.map { it.errorMessage }).containsExactly("Error 1", "Error 2", "Error 3")
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
}
