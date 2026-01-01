package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    private val fixedInstant = Instant.parse("2025-01-15T05:30:00Z") // KST 2025-01-15 14:30:00
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val rankingKeyGenerator = RankingKeyGenerator(fixedClock)

    @DisplayName("bucketKey(period, instant) 메서드 테스트")
    @Nested
    inner class PeriodAwareBucketKey {

        @DisplayName("HOURLY period로 ranking:products:hourly:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates hourly key with period`() {
            // given - KST 2025-01-15 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025011514")
        }

        @DisplayName("DAILY period로 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates daily key with period`() {
            // given - KST 2025-01-15 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }

        @DisplayName("HOURLY: 시간이 정시가 아니어도 정시로 truncate하여 키를 생성한다")
        @Test
        fun `truncates hourly to hour boundary`() {
            // given - KST 2025-12-31 23:59:59.999 (UTC 14:59:59.999)
            val instant = Instant.parse("2025-12-31T14:59:59.999999999Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025123123")
        }

        @DisplayName("HOURLY: UTC Instant를 Asia/Seoul 타임존으로 변환하여 키를 생성한다")
        @Test
        fun `converts UTC instant to Asia Seoul timezone for hourly`() {
            // given
            // UTC 2025-01-15 05:30:00 = KST 2025-01-15 14:30:00 (Asia/Seoul is UTC+9)
            val instant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025011514")
        }

        @DisplayName("HOURLY: 자정 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles midnight boundary correctly for hourly`() {
            // given - KST 2025-01-01 00:00:00 (UTC 2024-12-31 15:00:00)
            val instant = Instant.parse("2024-12-31T15:00:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025010100")
        }

        @DisplayName("DAILY: 연도 시작 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles year start boundary for daily`() {
            // given - KST 2025-01-01 00:00:00 (UTC 2024-12-31 15:00:00)
            val instant = Instant.parse("2024-12-31T15:00:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250101")
        }

        @DisplayName("DAILY: 연도 끝 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles year end boundary for daily`() {
            // given - KST 2025-12-31 23:59:59 (UTC 14:59:59)
            val instant = Instant.parse("2025-12-31T14:59:59Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20251231")
        }
    }
}
