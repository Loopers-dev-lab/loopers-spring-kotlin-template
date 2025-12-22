package com.loopers.support.idempotency

import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("EventHandledService 통합 테스트")
class EventHandledServiceIntegrationTest @Autowired constructor(
    private val eventHandledService: EventHandledService,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("isAlreadyHandled 테스트")
    @Nested
    inner class IsAlreadyHandled {

        @Test
        @DisplayName("존재하는 키는 true를 반환한다")
        fun `returns true for existing key`() {
            // given
            val idempotencyKey = "test-key-1"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            // when
            val result = eventHandledService.isAlreadyHandled(idempotencyKey)

            // then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("존재하지 않는 키는 false를 반환한다")
        fun `returns false for non-existing key`() {
            // given
            val idempotencyKey = "non-existing-key"

            // when
            val result = eventHandledService.isAlreadyHandled(idempotencyKey)

            // then
            assertThat(result).isFalse()
        }
    }

    @DisplayName("markAsHandled 테스트")
    @Nested
    inner class MarkAsHandled {

        @Test
        @DisplayName("성공적으로 저장된다")
        fun `saves idempotency key successfully`() {
            // given
            val idempotencyKey = "test-key-1"

            // when
            eventHandledService.markAsHandled(idempotencyKey)

            // then
            val result = eventHandledJpaRepository.existsByIdempotencyKey(idempotencyKey)
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("중복 키 저장 시 예외가 전파되지 않는다")
        fun `does not throw exception when saving duplicate key`() {
            // given
            val idempotencyKey = "duplicate-key"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            // when & then - 예외가 발생하지 않아야 함
            eventHandledService.markAsHandled(idempotencyKey)

            // 기존 레코드가 유지되는지 확인
            val count = eventHandledJpaRepository.findAll().count { it.idempotencyKey == idempotencyKey }
            assertThat(count).isEqualTo(1)
        }
    }

    @DisplayName("findAllExistingKeys 테스트")
    @Nested
    inner class FindAllExistingKeys {

        @Test
        @DisplayName("빈 세트 입력 시 빈 세트를 반환한다")
        fun `returns empty set for empty input`() {
            // given
            val keys = emptySet<String>()

            // when
            val result = eventHandledService.findAllExistingKeys(keys)

            // then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("존재하는 키들만 필터링하여 반환한다")
        fun `returns only existing keys`() {
            // given
            val existingKey1 = "existing-key-1"
            val existingKey2 = "existing-key-2"
            val nonExistingKey = "non-existing-key"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = existingKey1))
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = existingKey2))

            val keys = setOf(existingKey1, existingKey2, nonExistingKey)

            // when
            val result = eventHandledService.findAllExistingKeys(keys)

            // then
            assertThat(result).containsExactlyInAnyOrder(existingKey1, existingKey2)
            assertThat(result).doesNotContain(nonExistingKey)
        }
    }

    @DisplayName("markAllAsHandled 테스트")
    @Nested
    inner class MarkAllAsHandled {

        @Test
        @DisplayName("빈 리스트 입력 시 아무 작업도 하지 않는다")
        fun `does nothing for empty list`() {
            // given
            val keys = emptyList<String>()

            // when
            eventHandledService.markAllAsHandled(keys)

            // then
            val result = eventHandledJpaRepository.findAll()
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("여러 키를 배치로 저장한다")
        fun `saves multiple keys in batch`() {
            // given
            val keys = listOf("key-1", "key-2", "key-3")

            // when
            eventHandledService.markAllAsHandled(keys)

            // then
            val savedKeys = eventHandledJpaRepository.findAll().map { it.idempotencyKey }
            assertThat(savedKeys).containsExactlyInAnyOrderElementsOf(keys)
        }

        @Test
        @DisplayName("중복 키 포함 시 예외가 전파되지 않는다")
        fun `does not throw exception when some keys are duplicates`() {
            // given
            val existingKey = "existing-key"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = existingKey))

            val keys = listOf(existingKey, "new-key")

            // when & then - 예외가 발생하지 않아야 함
            eventHandledService.markAllAsHandled(keys)

            // 최소한 하나의 레코드가 존재하는지 확인
            val allKeys = eventHandledJpaRepository.findAll()
            assertThat(allKeys).isNotEmpty()
        }
    }
}
