package com.loopers.support.idempotency

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EventHandledService 단위 테스트")
class EventHandledServiceTest {

    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var eventHandledService: EventHandledService

    @BeforeEach
    fun setUp() {
        eventHandledRepository = mockk()
        eventHandledService = EventHandledService(eventHandledRepository)
    }

    @DisplayName("isAlreadyHandled()")
    @Nested
    inner class IsAlreadyHandled {

        @DisplayName("키가 존재하면 true를 반환한다")
        @Test
        fun `returns true when key exists`() {
            // given
            val idempotencyKey = "test-consumer:event-123"
            every { eventHandledRepository.existsByIdempotencyKey(idempotencyKey) } returns true

            // when
            val result = eventHandledService.isAlreadyHandled(idempotencyKey)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey(idempotencyKey) }
        }

        @DisplayName("키가 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when key does not exist`() {
            // given
            val idempotencyKey = "test-consumer:event-456"
            every { eventHandledRepository.existsByIdempotencyKey(idempotencyKey) } returns false

            // when
            val result = eventHandledService.isAlreadyHandled(idempotencyKey)

            // then
            assertThat(result).isFalse()
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey(idempotencyKey) }
        }
    }

    @DisplayName("markAsHandled()")
    @Nested
    inner class MarkAsHandled {

        @DisplayName("성공적으로 저장한다")
        @Test
        fun `saves successfully`() {
            // given
            val idempotencyKey = "test-consumer:event-789"
            every { eventHandledRepository.save(any()) } answers { firstArg() }

            // when
            eventHandledService.markAsHandled(idempotencyKey)

            // then
            verify(exactly = 1) {
                eventHandledRepository.save(
                    match { it.idempotencyKey == idempotencyKey },
                )
            }
        }

        @DisplayName("repository에서 예외가 발생해도 예외를 전파하지 않는다")
        @Test
        fun `does not throw when repository throws exception`() {
            // given
            val idempotencyKey = "test-consumer:event-duplicate"
            every { eventHandledRepository.save(any()) } throws RuntimeException("Duplicate key")

            // when & then - should not throw
            eventHandledService.markAsHandled(idempotencyKey)

            verify(exactly = 1) { eventHandledRepository.save(any()) }
        }
    }

    @DisplayName("findAllExistingKeys()")
    @Nested
    inner class FindAllExistingKeys {

        @DisplayName("존재하는 키들을 반환한다")
        @Test
        fun `returns existing keys`() {
            // given
            val keys = setOf("key1", "key2", "key3")
            val existingKeys = setOf("key1", "key3")
            every { eventHandledRepository.findAllExistingKeys(keys) } returns existingKeys

            // when
            val result = eventHandledService.findAllExistingKeys(keys)

            // then
            assertThat(result).containsExactlyInAnyOrder("key1", "key3")
            verify(exactly = 1) { eventHandledRepository.findAllExistingKeys(keys) }
        }

        @DisplayName("빈 입력에 대해 빈 Set을 반환한다")
        @Test
        fun `returns empty set for empty input`() {
            // given
            val keys = emptySet<String>()

            // when
            val result = eventHandledService.findAllExistingKeys(keys)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { eventHandledRepository.findAllExistingKeys(any()) }
        }
    }

    @DisplayName("markAllAsHandled()")
    @Nested
    inner class MarkAllAsHandled {

        @DisplayName("모든 키를 성공적으로 저장한다")
        @Test
        fun `saves all keys successfully`() {
            // given
            val idempotencyKeys = listOf("key1", "key2", "key3")
            every { eventHandledRepository.saveAll(any()) } answers { firstArg() }

            // when
            eventHandledService.markAllAsHandled(idempotencyKeys)

            // then
            verify(exactly = 1) {
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 3 &&
                            list.map { it.idempotencyKey }.containsAll(idempotencyKeys)
                    },
                )
            }
        }

        @DisplayName("repository에서 예외가 발생해도 예외를 전파하지 않는다")
        @Test
        fun `does not throw when repository throws exception`() {
            // given
            val idempotencyKeys = listOf("key1", "key2")
            every { eventHandledRepository.saveAll(any()) } throws RuntimeException("Batch save failed")

            // when & then - should not throw
            eventHandledService.markAllAsHandled(idempotencyKeys)

            verify(exactly = 1) { eventHandledRepository.saveAll(any()) }
        }

        @DisplayName("빈 리스트인 경우 repository를 호출하지 않는다")
        @Test
        fun `does not call repository for empty list`() {
            // given
            val idempotencyKeys = emptyList<String>()

            // when
            eventHandledService.markAllAsHandled(idempotencyKeys)

            // then
            verify(exactly = 0) { eventHandledRepository.saveAll(any()) }
        }
    }
}
