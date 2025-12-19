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
            val eventHandled = EventHandled.create(
                aggregateType = "Order",
                aggregateId = "123",
                action = "deductPoint",
            )

            // when
            val saved = eventHandledRepository.save(eventHandled)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.aggregateType).isEqualTo(eventHandled.aggregateType)
            assertThat(saved.aggregateId).isEqualTo(eventHandled.aggregateId)
            assertThat(saved.action).isEqualTo(eventHandled.action)
            assertThat(saved.handledAt).isNotNull()
        }
    }

    @DisplayName("existsByAggregateTypeAndAggregateIdAndAction()")
    @Nested
    inner class ExistsByAggregateTypeAndAggregateIdAndAction {

        @DisplayName("레코드가 없으면 false를 반환한다")
        @Test
        fun `returns false when no record exists`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "deductPoint"

            // when
            val exists = eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(exists).isFalse()
        }

        @DisplayName("일치하는 레코드가 있으면 true를 반환한다")
        @Test
        fun `returns true when matching record exists`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "deductPoint"

            eventHandledRepository.save(
                EventHandled.create(
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    action = action,
                ),
            )

            // when
            val exists = eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(exists).isTrue()
        }

        @DisplayName("부분 일치 (다른 action)일 경우 false를 반환한다")
        @Test
        fun `returns false for partial match with different action`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "deductPoint"

            eventHandledRepository.save(
                EventHandled.create(
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    action = action,
                ),
            )

            // when - 다른 action으로 조회
            val exists = eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = "updateStock",
            )

            // then
            assertThat(exists).isFalse()
        }
    }

    @DisplayName("유니크 제약조건")
    @Nested
    inner class UniqueConstraint {

        @DisplayName("중복된 (aggregate_type, aggregate_id, action) 조합은 저장할 수 없다")
        @Test
        fun `prevents duplicate aggregate_type aggregate_id action combination`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "deductPoint"

            eventHandledRepository.save(
                EventHandled.create(
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    action = action,
                ),
            )

            // when & then
            assertThrows<DataIntegrityViolationException> {
                eventHandledRepository.save(
                    EventHandled.create(
                        aggregateType = aggregateType,
                        aggregateId = aggregateId,
                        action = action,
                    ),
                )
            }
        }
    }
}
