package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("RankingPeriod 테스트")
class RankingPeriodTest {

    @DisplayName("fromString 메서드 테스트")
    @Nested
    inner class FromString {

        @DisplayName("null이 주어지면 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY when value is null`() {
            // given
            val value: String? = null

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("hourly가 주어지면 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY when value is hourly`() {
            // given
            val value = "hourly"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("HOURLY가 주어지면 HOURLY를 반환한다 (대소문자 무시)")
        @Test
        fun `returns HOURLY when value is HOURLY ignoring case`() {
            // given
            val value = "HOURLY"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("daily가 주어지면 DAILY를 반환한다")
        @Test
        fun `returns DAILY when value is daily`() {
            // given
            val value = "daily"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("DAILY가 주어지면 DAILY를 반환한다 (대소문자 무시)")
        @Test
        fun `returns DAILY when value is DAILY ignoring case`() {
            // given
            val value = "DAILY"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("Daily가 주어지면 DAILY를 반환한다 (혼합 대소문자)")
        @Test
        fun `returns DAILY when value is Daily with mixed case`() {
            // given
            val value = "Daily"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("알 수 없는 값이 주어지면 기본값으로 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY as default for unknown value`() {
            // given
            val value = "unknown"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("빈 문자열이 주어지면 기본값으로 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY as default for empty string`() {
            // given
            val value = ""

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }
    }

    @DisplayName("key 프로퍼티 테스트")
    @Nested
    inner class Key {

        @DisplayName("HOURLY의 key는 hourly이다")
        @Test
        fun `HOURLY key is hourly`() {
            // given
            val period = RankingPeriod.HOURLY

            // when
            val key = period.key

            // then
            assertThat(key).isEqualTo("hourly")
        }

        @DisplayName("DAILY의 key는 daily이다")
        @Test
        fun `DAILY key is daily`() {
            // given
            val period = RankingPeriod.DAILY

            // when
            val key = period.key

            // then
            assertThat(key).isEqualTo("daily")
        }
    }

    @DisplayName("subtractOne 메서드 테스트")
    @Nested
    inner class SubtractOne {

        @DisplayName("HOURLY는 1시간을 뺀다")
        @Test
        fun `HOURLY subtracts one hour`() {
            // given
            val dateTime = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val result = RankingPeriod.HOURLY.subtractOne(dateTime)

            // then
            assertThat(result).isEqualTo(ZonedDateTime.of(2024, 1, 15, 9, 30, 0, 0, ZoneId.of("Asia/Seoul")))
        }

        @DisplayName("HOURLY로 자정을 넘어가면 전날로 이동한다")
        @Test
        fun `HOURLY crossing midnight goes to previous day`() {
            // given
            val dateTime = ZonedDateTime.of(2024, 1, 15, 0, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val result = RankingPeriod.HOURLY.subtractOne(dateTime)

            // then
            assertThat(result).isEqualTo(ZonedDateTime.of(2024, 1, 14, 23, 30, 0, 0, ZoneId.of("Asia/Seoul")))
        }

        @DisplayName("DAILY는 1일을 뺀다")
        @Test
        fun `DAILY subtracts one day`() {
            // given
            val dateTime = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val result = RankingPeriod.DAILY.subtractOne(dateTime)

            // then
            assertThat(result).isEqualTo(ZonedDateTime.of(2024, 1, 14, 10, 30, 0, 0, ZoneId.of("Asia/Seoul")))
        }

        @DisplayName("DAILY로 월을 넘어가면 전달로 이동한다")
        @Test
        fun `DAILY crossing month goes to previous month`() {
            // given
            val dateTime = ZonedDateTime.of(2024, 2, 1, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val result = RankingPeriod.DAILY.subtractOne(dateTime)

            // then
            assertThat(result).isEqualTo(ZonedDateTime.of(2024, 1, 31, 10, 30, 0, 0, ZoneId.of("Asia/Seoul")))
        }
    }

    @DisplayName("fromKey 메서드 테스트")
    @Nested
    inner class FromKey {

        @DisplayName("hourly가 주어지면 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY when key is hourly`() {
            // given
            val key = "hourly"

            // when
            val result = RankingPeriod.fromKey(key)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("daily가 주어지면 DAILY를 반환한다")
        @Test
        fun `returns DAILY when key is daily`() {
            // given
            val key = "daily"

            // when
            val result = RankingPeriod.fromKey(key)

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("알 수 없는 key가 주어지면 예외를 던진다")
        @Test
        fun `throws exception for unknown key`() {
            // given
            val key = "unknown"

            // when & then
            assertThatThrownBy { RankingPeriod.fromKey(key) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("unknown")
        }
    }
}
