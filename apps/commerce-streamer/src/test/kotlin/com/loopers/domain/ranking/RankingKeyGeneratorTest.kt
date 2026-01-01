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

    @DisplayName("bucketKey(period, date: String) 메서드 테스트")
    @Nested
    inner class PeriodAwareBucketKeyFromString {

        @DisplayName("HOURLY period와 10자리 date로 hourly 키를 생성한다")
        @Test
        fun `generates hourly key from date string`() {
            // given
            val period = RankingPeriod.HOURLY
            val date = "2025011514"

            // when
            val key = rankingKeyGenerator.bucketKey(period, date)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025011514")
        }

        @DisplayName("DAILY period와 8자리 date로 daily 키를 생성한다")
        @Test
        fun `generates daily key from date string`() {
            // given
            val period = RankingPeriod.DAILY
            val date = "20250115"

            // when
            val key = rankingKeyGenerator.bucketKey(period, date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }
    }

    @DisplayName("currentBucketKey(period) 메서드 테스트")
    @Nested
    inner class PeriodAwareCurrentBucketKey {

        @DisplayName("HOURLY period로 Clock 기준 hourly 키를 생성한다")
        @Test
        fun `generates current hourly key with period using clock`() {
            // given - fixedClock is set to 2025-01-15T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)

            // then - KST 14:00 (truncated to hour boundary)
            assertThat(key).isEqualTo("ranking:products:hourly:2025011514")
        }

        @DisplayName("DAILY period로 Clock 기준 daily 키를 생성한다")
        @Test
        fun `generates current daily key with period using clock`() {
            // given - fixedClock is set to 2025-01-15T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)

            // then - KST 2025-01-15
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }
    }

    @DisplayName("previousBucketKey(bucketKey: String) 메서드 테스트")
    @Nested
    inner class PreviousBucketKeyFromString {

        @DisplayName("hourly 버킷 키에서 이전 시간의 버킷 키를 생성한다")
        @Test
        fun `generates previous hourly bucket key from string`() {
            // given
            val bucketKey = "ranking:products:hourly:2025011514"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:hourly:2025011513")
        }

        @DisplayName("daily 버킷 키에서 이전 날짜의 버킷 키를 생성한다")
        @Test
        fun `generates previous daily bucket key from string`() {
            // given
            val bucketKey = "ranking:products:daily:20250115"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:daily:20250114")
        }

        @DisplayName("자정 경계에서 이전 hourly 버킷 키를 올바르게 생성한다")
        @Test
        fun `handles midnight boundary for hourly bucket key`() {
            // given
            val bucketKey = "ranking:products:hourly:2025011500"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:hourly:2025011423")
        }

        @DisplayName("연도 경계에서 이전 hourly 버킷 키를 올바르게 생성한다")
        @Test
        fun `handles year boundary for hourly bucket key`() {
            // given
            val bucketKey = "ranking:products:hourly:2025010100"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:hourly:2024123123")
        }

        @DisplayName("연도 경계에서 이전 daily 버킷 키를 올바르게 생성한다")
        @Test
        fun `handles year boundary for daily bucket key`() {
            // given
            val bucketKey = "ranking:products:daily:20250101"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:daily:20241231")
        }
    }
}
