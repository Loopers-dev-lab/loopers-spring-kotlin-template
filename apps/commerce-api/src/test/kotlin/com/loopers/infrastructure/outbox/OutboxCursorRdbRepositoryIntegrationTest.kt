package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("OutboxCursorRdbRepository 통합 테스트")
class OutboxCursorRdbRepositoryIntegrationTest @Autowired constructor(
    private val outboxCursorRepository: OutboxCursorRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save()")
    @Nested
    inner class Save {

        @DisplayName("커서 레코드를 저장한다 (append-only)")
        @Test
        fun `persists cursor record`() {
            // given
            val cursor = OutboxCursor.create(lastProcessedId = 100L)

            // when
            val saved = outboxCursorRepository.save(cursor)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.lastProcessedId).isEqualTo(100L)
            assertThat(saved.createdAt).isNotNull()
        }

        @DisplayName("여러 커서 레코드를 순차적으로 저장한다")
        @Test
        fun `persists multiple cursor records sequentially`() {
            // given
            val cursor1 = OutboxCursor.create(lastProcessedId = 10L)
            val cursor2 = OutboxCursor.create(lastProcessedId = 20L)
            val cursor3 = OutboxCursor.create(lastProcessedId = 30L)

            // when
            val saved1 = outboxCursorRepository.save(cursor1)
            val saved2 = outboxCursorRepository.save(cursor2)
            val saved3 = outboxCursorRepository.save(cursor3)

            // then
            assertThat(saved1.id).isLessThan(saved2.id)
            assertThat(saved2.id).isLessThan(saved3.id)
            assertThat(saved1.lastProcessedId).isEqualTo(10L)
            assertThat(saved2.lastProcessedId).isEqualTo(20L)
            assertThat(saved3.lastProcessedId).isEqualTo(30L)
        }
    }

    @DisplayName("findLatest()")
    @Nested
    inner class FindLatest {

        @DisplayName("createdAt 기준 가장 최신 커서를 반환한다")
        @Test
        fun `returns most recent cursor by createdAt`() {
            // given
            outboxCursorRepository.save(OutboxCursor.create(lastProcessedId = 10L))
            outboxCursorRepository.save(OutboxCursor.create(lastProcessedId = 20L))
            val lastCursor = outboxCursorRepository.save(OutboxCursor.create(lastProcessedId = 30L))

            // when
            val latest = outboxCursorRepository.findLatest()

            // then
            assertThat(latest).isNotNull
            assertThat(latest!!.id).isEqualTo(lastCursor.id)
            assertThat(latest.lastProcessedId).isEqualTo(30L)
        }

        @DisplayName("커서가 없으면 null을 반환한다")
        @Test
        fun `returns null when no cursor exists`() {
            // when
            val latest = outboxCursorRepository.findLatest()

            // then
            assertThat(latest).isNull()
        }

        @DisplayName("하나의 커서만 있으면 해당 커서를 반환한다")
        @Test
        fun `returns the only cursor when single cursor exists`() {
            // given
            val cursor = outboxCursorRepository.save(OutboxCursor.create(lastProcessedId = 50L))

            // when
            val latest = outboxCursorRepository.findLatest()

            // then
            assertThat(latest).isNotNull
            assertThat(latest!!.id).isEqualTo(cursor.id)
            assertThat(latest.lastProcessedId).isEqualTo(50L)
        }
    }
}
