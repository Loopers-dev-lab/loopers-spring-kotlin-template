package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxRepository
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
@DisplayName("OutboxRdbRepository 통합 테스트")
class OutboxRdbRepositoryIntegrationTest @Autowired constructor(
    private val outboxRepository: OutboxRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save()")
    @Nested
    inner class Save {

        @DisplayName("Outbox를 저장하고 ID가 생성된 엔티티를 반환한다")
        @Test
        fun `persists Outbox and returns entity with generated id`() {
            // given
            val envelope = createCloudEventEnvelope(
                type = "loopers.order.created.v1",
                aggregateType = "Order",
                aggregateId = "123",
            )
            val outbox = Outbox.from(envelope)

            // when
            val saved = outboxRepository.save(outbox)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.eventId).isEqualTo(envelope.id)
            assertThat(saved.eventType).isEqualTo(envelope.type)
            assertThat(saved.source).isEqualTo(envelope.source)
            assertThat(saved.aggregateType).isEqualTo(envelope.aggregateType)
            assertThat(saved.aggregateId).isEqualTo(envelope.aggregateId)
            assertThat(saved.payload).isEqualTo(envelope.payload)
            assertThat(saved.createdAt).isEqualTo(envelope.time)
        }

        @DisplayName("여러 Outbox를 저장하면 순차적인 ID가 할당된다")
        @Test
        fun `persists multiple Outboxes with sequential IDs`() {
            // given
            val outbox1 = Outbox.from(createCloudEventEnvelope(aggregateId = "1"))
            val outbox2 = Outbox.from(createCloudEventEnvelope(aggregateId = "2"))
            val outbox3 = Outbox.from(createCloudEventEnvelope(aggregateId = "3"))

            // when
            val saved1 = outboxRepository.save(outbox1)
            val saved2 = outboxRepository.save(outbox2)
            val saved3 = outboxRepository.save(outbox3)

            // then
            assertThat(saved1.id).isLessThan(saved2.id)
            assertThat(saved2.id).isLessThan(saved3.id)
        }
    }

    @DisplayName("findAllByIdGreaterThanOrderByIdAsc()")
    @Nested
    inner class FindAllByIdGreaterThanOrderByIdAsc {

        @DisplayName("커서 이후의 레코드를 ID 오름차순으로 반환한다")
        @Test
        fun `returns records after cursor ordered by id`() {
            // given
            val outbox1 = outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "1")))
            val outbox2 = outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "2")))
            val outbox3 = outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "3")))
            val outbox4 = outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "4")))

            // when - 커서가 outbox1.id인 경우, outbox2, outbox3, outbox4만 반환
            val result = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(
                cursorId = outbox1.id,
                limit = 10,
            )

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo(outbox2.id)
            assertThat(result[1].id).isEqualTo(outbox3.id)
            assertThat(result[2].id).isEqualTo(outbox4.id)
        }

        @DisplayName("limit보다 많은 레코드가 있으면 limit 개수만큼만 반환한다")
        @Test
        fun `returns limited number of records`() {
            // given
            (1..10).forEach { i ->
                outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = i.toString())))
            }

            // when
            val result = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(
                cursorId = 0L,
                limit = 3,
            )

            // then
            assertThat(result).hasSize(3)
        }

        @DisplayName("커서가 0이면 모든 레코드를 반환한다")
        @Test
        fun `returns all records when cursor is 0`() {
            // given
            outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "1")))
            outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "2")))
            outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "3")))

            // when
            val result = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(
                cursorId = 0L,
                limit = 100,
            )

            // then
            assertThat(result).hasSize(3)
        }

        @DisplayName("커서 이후에 레코드가 없으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no records after cursor`() {
            // given
            val outbox = outboxRepository.save(Outbox.from(createCloudEventEnvelope(aggregateId = "1")))

            // when
            val result = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(
                cursorId = outbox.id,
                limit = 100,
            )

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("레코드가 없으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no records exist`() {
            // when
            val result = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(
                cursorId = 0L,
                limit = 100,
            )

            // then
            assertThat(result).isEmpty()
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
