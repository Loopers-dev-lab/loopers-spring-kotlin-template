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

    @DisplayName("of 메서드 테스트")
    @Nested
    inner class Of {

        @DisplayName("기본값으로 RankingQuery를 생성한다")
        @Test
        fun `creates RankingQuery with default values`() {
            // given
            val period = RankingPeriod.HOURLY

            // when
            val query = RankingQuery.of(period, null, null, null)

            // then
            assertThat(query.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(query.offset).isEqualTo(0L)
            assertThat(query.limit).isEqualTo(20L)
            assertThat(query.bucketKey).startsWith("ranking:products:")
            assertThat(query.fallbackKey).isNotNull()
        }

        @DisplayName("HOURLY period와 page/size로 RankingQuery를 생성한다")
        @Test
        fun `creates RankingQuery with HOURLY period and page size`() {
            // given
            val period = RankingPeriod.HOURLY
            val page = 2
            val size = 10

            // when
            val query = RankingQuery.of(period, null, page, size)

            // then
            assertThat(query.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(query.offset).isEqualTo(20L) // page 2 * size 10
            assertThat(query.limit).isEqualTo(10L)
        }

        @DisplayName("DAILY period로 RankingQuery를 생성한다")
        @Test
        fun `creates RankingQuery with DAILY period`() {
            // given
            val period = RankingPeriod.DAILY

            // when
            val query = RankingQuery.of(period, null, null, null)

            // then
            assertThat(query.period).isEqualTo(RankingPeriod.DAILY)
            assertThat(query.bucketKey).startsWith("ranking:products:daily:")
            assertThat(query.fallbackKey).startsWith("ranking:products:daily:")
        }

        @DisplayName("date가 지정되면 해당 date로 bucketKey를 생성한다")
        @Test
        fun `creates bucketKey from specified date for HOURLY`() {
            // given
            val period = RankingPeriod.HOURLY
            val date = "2025011514"

            // when
            val query = RankingQuery.of(period, date, null, null)

            // then
            assertThat(query.bucketKey).isEqualTo("ranking:products:2025011514")
        }

        @DisplayName("DAILY period와 date가 지정되면 daily bucketKey를 생성한다")
        @Test
        fun `creates daily bucketKey from specified date`() {
            // given
            val period = RankingPeriod.DAILY
            val date = "20250115"

            // when
            val query = RankingQuery.of(period, date, null, null)

            // then
            assertThat(query.bucketKey).isEqualTo("ranking:products:daily:20250115")
        }

        @DisplayName("date가 지정되면 fallbackKey는 null이다")
        @Test
        fun `fallbackKey is null when date is specified`() {
            // given
            val period = RankingPeriod.HOURLY
            val date = "2025011514"

            // when
            val query = RankingQuery.of(period, date, null, null)

            // then
            assertThat(query.fallbackKey).isNull()
        }

        @DisplayName("date가 지정되지 않으면 fallbackKey가 생성된다")
        @Test
        fun `fallbackKey is created when date is not specified`() {
            // given
            val period = RankingPeriod.HOURLY

            // when
            val query = RankingQuery.of(period, null, null, null)

            // then
            assertThat(query.fallbackKey).isNotNull()
            assertThat(query.fallbackKey).startsWith("ranking:products:")
        }

        @DisplayName("DAILY period에서 date가 지정되지 않으면 daily fallbackKey가 생성된다")
        @Test
        fun `daily fallbackKey is created when date is not specified for DAILY`() {
            // given
            val period = RankingPeriod.DAILY

            // when
            val query = RankingQuery.of(period, null, null, null)

            // then
            assertThat(query.fallbackKey).isNotNull()
            assertThat(query.fallbackKey).startsWith("ranking:products:daily:")
        }
    }

    @DisplayName("validation 테스트")
    @Nested
    inner class Validation {

        @ParameterizedTest
        @ValueSource(ints = [-1, -10, -100])
        @DisplayName("page가 음수이면 CoreException을 던진다 (offset 음수)")
        fun `throws CoreException when page is negative resulting in negative offset`(invalidPage: Int) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery.of(RankingPeriod.HOURLY, null, invalidPage, 20)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("offset")
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -10])
        @DisplayName("size가 0 이하이면 CoreException을 던진다")
        fun `throws CoreException when size is zero or negative`(invalidSize: Int) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery.of(RankingPeriod.HOURLY, null, 0, invalidSize)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("limit")
        }

        @ParameterizedTest
        @ValueSource(ints = [101, 200, 1000])
        @DisplayName("size가 최댓값(100)을 초과하면 CoreException을 던진다")
        fun `throws CoreException when size exceeds max limit`(invalidSize: Int) {
            // when
            val exception = assertThrows<CoreException> {
                RankingQuery.of(RankingPeriod.HOURLY, null, 0, invalidSize)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("limit")
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 10, 20, 50, 100])
        @DisplayName("유효한 size로 RankingQuery를 생성한다")
        fun `creates RankingQuery when valid size is given`(validSize: Int) {
            // when
            val query = RankingQuery.of(RankingPeriod.HOURLY, null, 0, validSize)

            // then
            assertThat(query.limit).isEqualTo(validSize.toLong())
        }
    }

    @DisplayName("limitForHasNext 메서드 테스트")
    @Nested
    inner class LimitForHasNext {

        @DisplayName("limitForHasNext는 limit + 1을 반환한다")
        @Test
        fun `limitForHasNext returns limit plus one`() {
            // given
            val query = RankingQuery.of(RankingPeriod.HOURLY, null, 0, 20)

            // when
            val result = query.limitForHasNext()

            // then
            assertThat(result).isEqualTo(21L)
        }

        @DisplayName("limit이 100일 때 limitForHasNext는 101을 반환한다")
        @Test
        fun `limitForHasNext returns 101 when limit is 100`() {
            // given
            val query = RankingQuery.of(RankingPeriod.HOURLY, null, 0, 100)

            // when
            val result = query.limitForHasNext()

            // then
            assertThat(result).isEqualTo(101L)
        }
    }
}
