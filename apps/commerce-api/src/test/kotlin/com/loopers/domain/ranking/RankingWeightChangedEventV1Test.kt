package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("RankingWeightChangedEventV1 단위 테스트")
class RankingWeightChangedEventV1Test {

    @DisplayName("create 메서드")
    @Nested
    inner class Create {

        @DisplayName("이벤트 생성 시 현재 시각으로 occurredAt이 설정된다")
        @Test
        fun `creates event with current timestamp as occurredAt`() {
            // given
            val beforeCreation = Instant.now()

            // when
            val event = RankingWeightChangedEventV1.create()

            // then
            val afterCreation = Instant.now()
            assertThat(event.occurredAt).isBetween(beforeCreation, afterCreation)
        }

        @DisplayName("생성된 이벤트는 DomainEvent를 구현한다")
        @Test
        fun `created event implements DomainEvent`() {
            // when
            val event = RankingWeightChangedEventV1.create()

            // then
            assertThat(event).isInstanceOf(com.loopers.support.event.DomainEvent::class.java)
        }
    }

    @DisplayName("생성자")
    @Nested
    inner class Constructor {

        @DisplayName("특정 occurredAt으로 이벤트를 생성할 수 있다")
        @Test
        fun `can create event with specific occurredAt`() {
            // given
            val specificTime = Instant.parse("2024-01-15T10:30:00Z")

            // when
            val event = RankingWeightChangedEventV1(occurredAt = specificTime)

            // then
            assertThat(event.occurredAt).isEqualTo(specificTime)
        }

        @DisplayName("기본 생성자는 현재 시각을 사용한다")
        @Test
        fun `default constructor uses current time`() {
            // given
            val beforeCreation = Instant.now().truncatedTo(ChronoUnit.MILLIS)

            // when
            val event = RankingWeightChangedEventV1()

            // then
            val afterCreation = Instant.now()
            assertThat(event.occurredAt).isBetween(beforeCreation, afterCreation)
        }
    }
}
