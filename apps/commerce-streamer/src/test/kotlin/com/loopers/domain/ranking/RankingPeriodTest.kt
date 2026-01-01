package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("RankingPeriod 테스트")
class RankingPeriodTest {

    @DisplayName("subtractOne(instant) 메서드 테스트")
    @Nested
    inner class SubtractOne {

        @DisplayName("HOURLY: 1시간을 뺀다")
        @Test
        fun `subtracts one hour for HOURLY period`() {
            // given
            val instant = Instant.parse("2025-01-15T14:30:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-15T13:30:00Z"))
        }

        @DisplayName("DAILY: 1일(24시간)을 뺀다")
        @Test
        fun `subtracts one day for DAILY period`() {
            // given
            val instant = Instant.parse("2025-01-15T14:30:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-14T14:30:00Z"))
        }

        @DisplayName("HOURLY: 자정 경계에서 이전 날짜로 넘어간다")
        @Test
        fun `crosses midnight boundary for HOURLY period`() {
            // given
            val instant = Instant.parse("2025-01-15T00:00:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-14T23:00:00Z"))
        }

        @DisplayName("HOURLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `crosses year boundary for HOURLY period`() {
            // given
            val instant = Instant.parse("2025-01-01T00:00:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-31T23:00:00Z"))
        }

        @DisplayName("DAILY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `crosses year boundary for DAILY period`() {
            // given
            val instant = Instant.parse("2025-01-01T00:00:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-31T00:00:00Z"))
        }

        @DisplayName("DAILY: 월 경계에서 이전 월로 넘어간다")
        @Test
        fun `crosses month boundary for DAILY period`() {
            // given
            val instant = Instant.parse("2025-03-01T12:00:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-02-28T12:00:00Z"))
        }

        @DisplayName("HOURLY: Instant의 나노초는 유지된다")
        @Test
        fun `preserves nanoseconds for HOURLY period`() {
            // given
            val instant = Instant.parse("2025-01-15T14:30:00.123456789Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-15T13:30:00.123456789Z"))
        }

        @DisplayName("WEEKLY: 7일(168시간)을 뺀다")
        @Test
        fun `subtracts seven days for WEEKLY period`() {
            // given
            val instant = Instant.parse("2025-01-15T14:30:00Z")

            // when
            val result = RankingPeriod.WEEKLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-08T14:30:00Z"))
        }

        @DisplayName("MONTHLY: 30일을 뺀다")
        @Test
        fun `subtracts thirty days for MONTHLY period`() {
            // given
            val instant = Instant.parse("2025-01-31T14:30:00Z")

            // when
            val result = RankingPeriod.MONTHLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2025-01-01T14:30:00Z"))
        }

        @DisplayName("WEEKLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `crosses year boundary for WEEKLY period`() {
            // given
            val instant = Instant.parse("2025-01-05T00:00:00Z")

            // when
            val result = RankingPeriod.WEEKLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-29T00:00:00Z"))
        }

        @DisplayName("MONTHLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `crosses year boundary for MONTHLY period`() {
            // given
            val instant = Instant.parse("2025-01-15T00:00:00Z")

            // when
            val result = RankingPeriod.MONTHLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-16T00:00:00Z"))
        }
    }

    @DisplayName("fromString(value) 메서드 테스트")
    @Nested
    inner class FromString {

        @DisplayName("'daily' 문자열은 DAILY를 반환한다")
        @Test
        fun `returns DAILY for daily string`() {
            // when
            val result = RankingPeriod.fromString("daily")

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("'DAILY' 대문자도 DAILY를 반환한다")
        @Test
        fun `returns DAILY for uppercase DAILY string`() {
            // when
            val result = RankingPeriod.fromString("DAILY")

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("null은 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY for null`() {
            // when
            val result = RankingPeriod.fromString(null)

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("알 수 없는 값은 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY for unknown value`() {
            // when
            val result = RankingPeriod.fromString("unknown")

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("'weekly' 문자열은 WEEKLY를 반환한다")
        @Test
        fun `returns WEEKLY for weekly string`() {
            // when
            val result = RankingPeriod.fromString("weekly")

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("'WEEKLY' 대문자도 WEEKLY를 반환한다")
        @Test
        fun `returns WEEKLY for uppercase WEEKLY string`() {
            // when
            val result = RankingPeriod.fromString("WEEKLY")

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("'monthly' 문자열은 MONTHLY를 반환한다")
        @Test
        fun `returns MONTHLY for monthly string`() {
            // when
            val result = RankingPeriod.fromString("monthly")

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
        }

        @DisplayName("'MONTHLY' 대문자도 MONTHLY를 반환한다")
        @Test
        fun `returns MONTHLY for uppercase MONTHLY string`() {
            // when
            val result = RankingPeriod.fromString("MONTHLY")

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
        }
    }

    @DisplayName("fromKey(key) 메서드 테스트")
    @Nested
    inner class FromKey {

        @DisplayName("'hourly' 키는 HOURLY를 반환한다")
        @Test
        fun `returns HOURLY for hourly key`() {
            // when
            val result = RankingPeriod.fromKey("hourly")

            // then
            assertThat(result).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("'daily' 키는 DAILY를 반환한다")
        @Test
        fun `returns DAILY for daily key`() {
            // when
            val result = RankingPeriod.fromKey("daily")

            // then
            assertThat(result).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("알 수 없는 키는 예외를 발생시킨다")
        @Test
        fun `throws exception for unknown key`() {
            // when & then
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                RankingPeriod.fromKey("unknown")
            }.also { exception ->
                assertThat(exception.message).contains("Unknown RankingPeriod key: unknown")
            }
        }

        @DisplayName("'weekly' 키는 WEEKLY를 반환한다")
        @Test
        fun `returns WEEKLY for weekly key`() {
            // when
            val result = RankingPeriod.fromKey("weekly")

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("'monthly' 키는 MONTHLY를 반환한다")
        @Test
        fun `returns MONTHLY for monthly key`() {
            // when
            val result = RankingPeriod.fromKey("monthly")

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
        }
    }
}
