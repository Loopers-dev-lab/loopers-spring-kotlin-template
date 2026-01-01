package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("RankingQuery 테스트")
class RankingQueryTest {

    @DisplayName("생성 테스트")
    @Nested
    inner class Creation {

        @DisplayName("기본값으로 RankingQuery를 생성한다")
        @Test
        fun `creates RankingQuery with valid values`() {
            // given
            val period = RankingPeriod.HOURLY
            val bucketKey = "ranking:products:hourly:2025011514"
            val offset = 0L
            val limit = 20L

            // when
            val query = RankingQuery(
                period = period,
                bucketKey = bucketKey,
                offset = offset,
                limit = limit,
            )

            // then
            assertThat(query.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(query.bucketKey).isEqualTo(bucketKey)
            assertThat(query.offset).isEqualTo(0L)
            assertThat(query.limit).isEqualTo(20L)
        }

        @DisplayName("DAILY period로 RankingQuery를 생성한다")
        @Test
        fun `creates RankingQuery with DAILY period`() {
            // given
            val period = RankingPeriod.DAILY
            val bucketKey = "ranking:products:daily:20250115"

            // when
            val query = RankingQuery(
                period = period,
                bucketKey = bucketKey,
                offset = 0L,
                limit = 20L,
            )

            // then
            assertThat(query.period).isEqualTo(RankingPeriod.DAILY)
            assertThat(query.bucketKey).isEqualTo(bucketKey)
        }

        @DisplayName("offset과 limit으로 페이지네이션 설정을 한다")
        @Test
        fun `creates RankingQuery with pagination settings`() {
            // given
            val offset = 20L
            val limit = 10L

            // when
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:hourly:2025011514",
                offset = offset,
                limit = limit,
            )

            // then
            assertThat(query.offset).isEqualTo(20L)
            assertThat(query.limit).isEqualTo(10L)
        }
    }

    @DisplayName("validation 테스트")
    @Nested
    inner class Validation {

        @ParameterizedTest
        @ValueSource(longs = [-1, -10, -100])
        @DisplayName("offset이 음수이면 CoreException을 던진다")
        fun `throws CoreException when offset is negative`(invalidOffset: Long) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery(
                    period = RankingPeriod.HOURLY,
                    bucketKey = "ranking:products:hourly:2025011514",
                    offset = invalidOffset,
                    limit = 20L,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("offset")
        }

        @ParameterizedTest
        @ValueSource(longs = [0, -1, -10])
        @DisplayName("limit이 0 이하이면 CoreException을 던진다")
        fun `throws CoreException when limit is zero or negative`(invalidLimit: Long) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery(
                    period = RankingPeriod.HOURLY,
                    bucketKey = "ranking:products:hourly:2025011514",
                    offset = 0L,
                    limit = invalidLimit,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("limit")
        }

        @ParameterizedTest
        @ValueSource(longs = [101, 200, 1000])
        @DisplayName("limit이 최댓값(100)을 초과하면 CoreException을 던진다")
        fun `throws CoreException when limit exceeds max limit`(invalidLimit: Long) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery(
                    period = RankingPeriod.HOURLY,
                    bucketKey = "ranking:products:hourly:2025011514",
                    offset = 0L,
                    limit = invalidLimit,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("limit")
        }

        @ParameterizedTest
        @ValueSource(longs = [1, 10, 20, 50, 100])
        @DisplayName("유효한 limit으로 RankingQuery를 생성한다")
        fun `creates RankingQuery when valid limit is given`(validLimit: Long) {
            // when
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:hourly:2025011514",
                offset = 0L,
                limit = validLimit,
            )

            // then
            assertThat(query.limit).isEqualTo(validLimit)
        }
    }

    @DisplayName("copy 메서드 테스트")
    @Nested
    inner class CopyMethod {

        @DisplayName("bucketKey만 변경하여 copy할 수 있다")
        @Test
        fun `can copy with different bucketKey`() {
            // given
            val original = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:hourly:2025011514",
                offset = 0L,
                limit = 20L,
            )

            // when
            val copied = original.copy(bucketKey = "ranking:products:hourly:2025011513")

            // then
            assertThat(copied.bucketKey).isEqualTo("ranking:products:hourly:2025011513")
            assertThat(copied.period).isEqualTo(original.period)
            assertThat(copied.offset).isEqualTo(original.offset)
            assertThat(copied.limit).isEqualTo(original.limit)
        }
    }
}
