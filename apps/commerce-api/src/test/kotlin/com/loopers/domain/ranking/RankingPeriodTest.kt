package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

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

        @DisplayName("weekly가 주어지면 WEEKLY를 반환한다")
        @Test
        fun `returns WEEKLY when value is weekly`() {
            // given
            val value = "weekly"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("WEEKLY가 주어지면 WEEKLY를 반환한다 (대소문자 무시)")
        @Test
        fun `returns WEEKLY when value is WEEKLY ignoring case`() {
            // given
            val value = "WEEKLY"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("Weekly가 주어지면 WEEKLY를 반환한다 (혼합 대소문자)")
        @Test
        fun `returns WEEKLY when value is Weekly with mixed case`() {
            // given
            val value = "Weekly"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("monthly가 주어지면 MONTHLY를 반환한다")
        @Test
        fun `returns MONTHLY when value is monthly`() {
            // given
            val value = "monthly"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
        }

        @DisplayName("MONTHLY가 주어지면 MONTHLY를 반환한다 (대소문자 무시)")
        @Test
        fun `returns MONTHLY when value is MONTHLY ignoring case`() {
            // given
            val value = "MONTHLY"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
        }

        @DisplayName("Monthly가 주어지면 MONTHLY를 반환한다 (혼합 대소문자)")
        @Test
        fun `returns MONTHLY when value is Monthly with mixed case`() {
            // given
            val value = "Monthly"

            // when
            val result = RankingPeriod.fromString(value)

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
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

        @DisplayName("WEEKLY의 key는 weekly이다")
        @Test
        fun `WEEKLY key is weekly`() {
            // given
            val period = RankingPeriod.WEEKLY

            // when
            val key = period.key

            // then
            assertThat(key).isEqualTo("weekly")
        }

        @DisplayName("MONTHLY의 key는 monthly이다")
        @Test
        fun `MONTHLY key is monthly`() {
            // given
            val period = RankingPeriod.MONTHLY

            // when
            val key = period.key

            // then
            assertThat(key).isEqualTo("monthly")
        }
    }

    @DisplayName("subtractOne 메서드 테스트")
    @Nested
    inner class SubtractOne {

        @DisplayName("HOURLY는 1시간을 뺀다")
        @Test
        fun `HOURLY subtracts one hour`() {
            // given
            val instant = Instant.parse("2024-01-15T01:30:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-15T00:30:00Z"))
        }

        @DisplayName("HOURLY로 자정을 넘어가면 전날로 이동한다")
        @Test
        fun `HOURLY crossing midnight goes to previous day`() {
            // given
            val instant = Instant.parse("2024-01-15T00:30:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-14T23:30:00Z"))
        }

        @DisplayName("DAILY는 1일(24시간)을 뺀다")
        @Test
        fun `DAILY subtracts one day`() {
            // given
            val instant = Instant.parse("2024-01-15T10:30:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-14T10:30:00Z"))
        }

        @DisplayName("DAILY로 월을 넘어가면 전달로 이동한다")
        @Test
        fun `DAILY crossing month goes to previous month`() {
            // given
            val instant = Instant.parse("2024-02-01T10:30:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-31T10:30:00Z"))
        }

        @DisplayName("HOURLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `HOURLY crosses year boundary to previous year`() {
            // given
            val instant = Instant.parse("2025-01-01T00:00:00Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-31T23:00:00Z"))
        }

        @DisplayName("DAILY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `DAILY crosses year boundary to previous year`() {
            // given
            val instant = Instant.parse("2025-01-01T00:00:00Z")

            // when
            val result = RankingPeriod.DAILY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-31T00:00:00Z"))
        }

        @DisplayName("HOURLY: Instant의 나노초는 유지된다")
        @Test
        fun `HOURLY preserves nanoseconds`() {
            // given
            val instant = Instant.parse("2024-01-15T14:30:00.123456789Z")

            // when
            val result = RankingPeriod.HOURLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-15T13:30:00.123456789Z"))
        }

        @DisplayName("WEEKLY는 7일을 뺀다")
        @Test
        fun `WEEKLY subtracts seven days`() {
            // given
            val instant = Instant.parse("2024-01-15T10:30:00Z")

            // when
            val result = RankingPeriod.WEEKLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-08T10:30:00Z"))
        }

        @DisplayName("WEEKLY로 월을 넘어가면 전달로 이동한다")
        @Test
        fun `WEEKLY crossing month goes to previous month`() {
            // given
            val instant = Instant.parse("2024-02-03T10:30:00Z")

            // when
            val result = RankingPeriod.WEEKLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-27T10:30:00Z"))
        }

        @DisplayName("WEEKLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `WEEKLY crosses year boundary to previous year`() {
            // given
            val instant = Instant.parse("2025-01-03T00:00:00Z")

            // when
            val result = RankingPeriod.WEEKLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-27T00:00:00Z"))
        }

        @DisplayName("MONTHLY는 30일을 뺀다")
        @Test
        fun `MONTHLY subtracts thirty days`() {
            // given
            val instant = Instant.parse("2024-03-15T10:30:00Z")

            // when
            val result = RankingPeriod.MONTHLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-02-14T10:30:00Z"))
        }

        @DisplayName("MONTHLY로 월을 넘어가면 전달로 이동한다")
        @Test
        fun `MONTHLY crossing month goes to previous month`() {
            // given
            val instant = Instant.parse("2024-02-15T10:30:00Z")

            // when
            val result = RankingPeriod.MONTHLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-01-16T10:30:00Z"))
        }

        @DisplayName("MONTHLY: 연도 경계에서 이전 연도로 넘어간다")
        @Test
        fun `MONTHLY crosses year boundary to previous year`() {
            // given
            val instant = Instant.parse("2025-01-15T00:00:00Z")

            // when
            val result = RankingPeriod.MONTHLY.subtractOne(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2024-12-16T00:00:00Z"))
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

        @DisplayName("weekly가 주어지면 WEEKLY를 반환한다")
        @Test
        fun `returns WEEKLY when key is weekly`() {
            // given
            val key = "weekly"

            // when
            val result = RankingPeriod.fromKey(key)

            // then
            assertThat(result).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("monthly가 주어지면 MONTHLY를 반환한다")
        @Test
        fun `returns MONTHLY when key is monthly`() {
            // given
            val key = "monthly"

            // when
            val result = RankingPeriod.fromKey(key)

            // then
            assertThat(result).isEqualTo(RankingPeriod.MONTHLY)
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
