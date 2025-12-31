package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
}
