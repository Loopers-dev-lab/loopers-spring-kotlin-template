package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.support.idempotency.IdempotencyResult
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("EventHandledRdbRepository 통합 테스트")
class EventHandledRdbRepositoryIntegrationTest @Autowired constructor(
    private val eventHandledRepository: EventHandledRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save()")
    @Nested
    inner class Save {

        @DisplayName("EventHandled 저장 성공 시 IdempotencyResult.Recorded를 반환한다")
        @Test
        fun `returns Recorded on successful save`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // when
            val result = eventHandledRepository.save(eventHandled)

            // then
            assertThat(result).isEqualTo(IdempotencyResult.Recorded)
        }

        @DisplayName("저장된 EventHandled가 데이터베이스에 존재하는지 확인한다")
        @Test
        fun `persisted EventHandled exists in database`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // when
            eventHandledRepository.save(eventHandled)

            // then
            assertThat(eventHandledRepository.existsByIdempotencyKey(idempotencyKey)).isTrue()
        }

        @DisplayName("중복 키 저장 시 IdempotencyResult.RecordFailed를 반환한다")
        @Test
        fun `returns RecordFailed on duplicate key`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"
            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))

            // when
            val result = eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))

            // then
            assertThat(result).isEqualTo(IdempotencyResult.RecordFailed)
        }
    }

    @DisplayName("saveAll()")
    @Nested
    inner class SaveAll {

        @DisplayName("여러 EventHandled 저장 성공 시 IdempotencyResult.Recorded를 반환한다")
        @Test
        fun `returns Recorded on successful saveAll`() {
            // given
            val eventHandledList = listOf(
                EventHandled(idempotencyKey = "ranking:view:event-1"),
                EventHandled(idempotencyKey = "ranking:view:event-2"),
                EventHandled(idempotencyKey = "ranking:view:event-3"),
            )

            // when
            val result = eventHandledRepository.saveAll(eventHandledList)

            // then
            assertThat(result).isEqualTo(IdempotencyResult.Recorded)
        }

        @DisplayName("빈 리스트 저장 시 IdempotencyResult.Recorded를 반환한다")
        @Test
        fun `returns Recorded on empty list`() {
            // when
            val result = eventHandledRepository.saveAll(emptyList())

            // then
            assertThat(result).isEqualTo(IdempotencyResult.Recorded)
        }

        @DisplayName("중복 키가 포함된 리스트 저장 시 IdempotencyResult.RecordFailed를 반환한다")
        @Test
        fun `returns RecordFailed when list contains duplicate key`() {
            // given
            eventHandledRepository.save(EventHandled(idempotencyKey = "ranking:view:event-1"))

            val eventHandledList = listOf(
                // duplicate key
                EventHandled(idempotencyKey = "ranking:view:event-1"),
                EventHandled(idempotencyKey = "ranking:view:event-2"),
            )

            // when
            val result = eventHandledRepository.saveAll(eventHandledList)

            // then
            assertThat(result).isEqualTo(IdempotencyResult.RecordFailed)
        }
    }

    @DisplayName("existsByIdempotencyKey()")
    @Nested
    inner class ExistsByIdempotencyKey {

        @DisplayName("레코드가 없으면 false를 반환한다")
        @Test
        fun `returns false when no record exists`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            // when
            val exists = eventHandledRepository.existsByIdempotencyKey(idempotencyKey)

            // then
            assertThat(exists).isFalse()
        }

        @DisplayName("일치하는 레코드가 있으면 true를 반환한다")
        @Test
        fun `returns true when matching record exists`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))

            // when
            val exists = eventHandledRepository.existsByIdempotencyKey(idempotencyKey)

            // then
            assertThat(exists).isTrue()
        }

        @DisplayName("다른 idempotencyKey로 조회시 false를 반환한다")
        @Test
        fun `returns false for different idempotencyKey`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"
            val differentKey = "product-statistic:Order:456:paid"

            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))

            // when
            val exists = eventHandledRepository.existsByIdempotencyKey(differentKey)

            // then
            assertThat(exists).isFalse()
        }
    }
}
