package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

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

        @DisplayName("EventHandled를 저장하고 ID가 생성된 엔티티를 반환한다")
        @Test
        fun `persists EventHandled and returns entity with generated id greater than 0`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // when
            val saved = eventHandledRepository.save(eventHandled)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.idempotencyKey).isEqualTo(idempotencyKey)
            assertThat(saved.handledAt).isNotNull()
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

    @DisplayName("유니크 제약조건")
    @Nested
    inner class UniqueConstraint {

        @DisplayName("중복된 idempotencyKey는 저장할 수 없다")
        @Test
        fun `prevents duplicate idempotencyKey`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))

            // when & then
            assertThrows<DataIntegrityViolationException> {
                eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
            }
        }
    }
}
