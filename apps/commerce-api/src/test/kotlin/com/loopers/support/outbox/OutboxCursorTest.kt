package com.loopers.support.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("OutboxCursor 단위 테스트")
class OutboxCursorTest {

    @DisplayName("create() 팩토리 메서드")
    @Nested
    inner class CreateFactory {

        @DisplayName("lastProcessedId를 설정하여 OutboxCursor를 생성한다")
        @Test
        fun `creates OutboxCursor with lastProcessedId`() {
            // given
            val lastProcessedId = 100L
            val beforeCreate = Instant.now()

            // when
            val cursor = OutboxCursor.create(lastProcessedId)
            val afterCreate = Instant.now()

            // then
            assertThat(cursor.id).isEqualTo(0L) // Not persisted yet
            assertThat(cursor.lastProcessedId).isEqualTo(lastProcessedId)
            assertThat(cursor.createdAt).isAfterOrEqualTo(beforeCreate)
            assertThat(cursor.createdAt).isBeforeOrEqualTo(afterCreate)
        }

        @DisplayName("lastProcessedId가 0인 경우에도 정상적으로 생성한다")
        @Test
        fun `creates OutboxCursor with lastProcessedId zero`() {
            // given
            val lastProcessedId = 0L

            // when
            val cursor = OutboxCursor.create(lastProcessedId)

            // then
            assertThat(cursor.lastProcessedId).isEqualTo(0L)
        }
    }

    @DisplayName("필드 검증")
    @Nested
    inner class FieldValidation {

        @DisplayName("id는 생성 시 0으로 초기화된다")
        @Test
        fun `id is initialized to 0 on creation`() {
            // when
            val cursor = OutboxCursor.create(50L)

            // then
            assertThat(cursor.id).isEqualTo(0L)
        }

        @DisplayName("createdAt은 현재 시각으로 설정된다")
        @Test
        fun `createdAt is set to current time`() {
            // given
            val now = Instant.now()

            // when
            val cursor = OutboxCursor.create(1L)

            // then
            assertThat(cursor.createdAt)
                .isAfterOrEqualTo(now)
                .isBefore(now.plusSeconds(1))
        }
    }
}
