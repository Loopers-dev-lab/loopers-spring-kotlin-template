package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.RankingKeyGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    private val fixedInstant = Instant.parse("2025-01-26T05:30:00Z") // KST 2025-01-26 14:30:00
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val rankingKeyGenerator = RankingKeyGenerator(fixedClock)

    @DisplayName("bucketKey(period, instant) 메서드 테스트")
    @Nested
    inner class BucketKeyWithPeriodAndInstant {

        @DisplayName("HOURLY period에서 ranking:products:hourly:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates hourly key format`() {
            // given - KST 2025-01-26 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-26T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("DAILY period에서 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates daily key format`() {
            // given - KST 2025-01-26 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-26T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250126")
        }

        @DisplayName("WEEKLY period에서 ranking:products:weekly:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates weekly key format`() {
            // given - KST 2025-01-26 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-26T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.WEEKLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:weekly:20250126")
        }

        @DisplayName("MONTHLY period에서 ranking:products:monthly:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates monthly key format`() {
            // given - KST 2025-01-26 14:30:00 (UTC 05:30:00)
            val instant = Instant.parse("2025-01-26T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.MONTHLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:monthly:20250126")
        }

        @DisplayName("UTC Instant를 Asia/Seoul 타임존으로 변환하여 키를 생성한다")
        @Test
        fun `converts UTC instant to Asia Seoul timezone`() {
            // given
            // UTC 2025-01-26 05:30:00 = KST 2025-01-26 14:30:00 (Asia/Seoul is UTC+9)
            val instant = Instant.parse("2025-01-26T05:30:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("시간이 정시가 아니어도 정시로 truncate하여 키를 생성한다")
        @Test
        fun `truncates to hour boundary`() {
            // given - KST 2025-12-31 23:59:59.999 (UTC 14:59:59.999)
            val instant = Instant.parse("2025-12-31T14:59:59.999999999Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025123123")
        }

        @DisplayName("자정 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles midnight boundary correctly`() {
            // given - KST 2025-01-01 00:00:00 (UTC 2024-12-31 15:00:00)
            val instant = Instant.parse("2024-12-31T15:00:00Z")

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, instant)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025010100")
        }
    }

    @DisplayName("bucketKey(period, date) 메서드 테스트")
    @Nested
    inner class BucketKeyWithPeriodAndDateString {

        @DisplayName("HOURLY period와 10자리 날짜 문자열로 키를 생성한다")
        @Test
        fun `generates hourly key from date string`() {
            // given
            val dateString = "2025012614"

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, dateString)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("DAILY period와 8자리 날짜 문자열로 키를 생성한다")
        @Test
        fun `generates daily key from date string`() {
            // given
            val dateString = "20250126"

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, dateString)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250126")
        }

        @DisplayName("WEEKLY period와 8자리 날짜 문자열로 키를 생성한다")
        @Test
        fun `generates weekly key from date string`() {
            // given
            val dateString = "20250126"

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.WEEKLY, dateString)

            // then
            assertThat(key).isEqualTo("ranking:products:weekly:20250126")
        }

        @DisplayName("MONTHLY period와 8자리 날짜 문자열로 키를 생성한다")
        @Test
        fun `generates monthly key from date string`() {
            // given
            val dateString = "20250126"

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.MONTHLY, dateString)

            // then
            assertThat(key).isEqualTo("ranking:products:monthly:20250126")
        }
    }

    @DisplayName("currentBucketKey(period) 메서드 테스트")
    @Nested
    inner class CurrentBucketKeyWithPeriod {

        @DisplayName("HOURLY period에서 Clock 기준으로 ranking:products:hourly: prefix를 가진 키를 생성한다")
        @Test
        fun `generates hourly key with clock`() {
            // given - fixedClock is set to 2025-01-26T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)

            // then - KST 14:00 (truncated to hour boundary)
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("DAILY period에서 Clock 기준으로 ranking:products:daily: prefix를 가진 키를 생성한다")
        @Test
        fun `generates daily key with clock`() {
            // given - fixedClock is set to 2025-01-26T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)

            // then - KST 2025-01-26
            assertThat(key).isEqualTo("ranking:products:daily:20250126")
        }

        @DisplayName("WEEKLY period에서 Clock 기준으로 ranking:products:weekly: prefix를 가진 키를 생성한다")
        @Test
        fun `generates weekly key with clock`() {
            // given - fixedClock is set to 2025-01-26T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.WEEKLY)

            // then - KST 2025-01-26
            assertThat(key).isEqualTo("ranking:products:weekly:20250126")
        }

        @DisplayName("MONTHLY period에서 Clock 기준으로 ranking:products:monthly: prefix를 가진 키를 생성한다")
        @Test
        fun `generates monthly key with clock`() {
            // given - fixedClock is set to 2025-01-26T05:30:00Z (KST 14:30)

            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.MONTHLY)

            // then - KST 2025-01-26
            assertThat(key).isEqualTo("ranking:products:monthly:20250126")
        }
    }

    @DisplayName("previousBucketKey(bucketKey) 메서드 테스트")
    @Nested
    inner class PreviousBucketKeyFromKey {

        @DisplayName("hourly 버킷 키에서 이전 시간의 버킷 키를 반환한다")
        @Test
        fun `returns previous hour bucket key`() {
            // given
            val bucketKey = "ranking:products:hourly:2025012614"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:hourly:2025012613")
        }

        @DisplayName("daily 버킷 키에서 이전 날의 버킷 키를 반환한다")
        @Test
        fun `returns previous day bucket key`() {
            // given
            val bucketKey = "ranking:products:daily:20250126"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:daily:20250125")
        }

        @DisplayName("weekly 버킷 키에서 7일 전의 버킷 키를 반환한다")
        @Test
        fun `returns previous week bucket key`() {
            // given
            val bucketKey = "ranking:products:weekly:20250126"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then - 7 days before 2025-01-26 is 2025-01-19
            assertThat(previousKey).isEqualTo("ranking:products:weekly:20250119")
        }

        @DisplayName("monthly 버킷 키에서 30일 전의 버킷 키를 반환한다")
        @Test
        fun `returns previous month bucket key`() {
            // given
            val bucketKey = "ranking:products:monthly:20250126"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then - 30 days before 2025-01-26 is 2024-12-27
            assertThat(previousKey).isEqualTo("ranking:products:monthly:20241227")
        }

        @DisplayName("자정 경계에서 이전 날로 올바르게 전환한다")
        @Test
        fun `handles midnight boundary correctly for hourly`() {
            // given
            val bucketKey = "ranking:products:hourly:2025012600"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:hourly:2025012523")
        }

        @DisplayName("월 경계에서 이전 달로 올바르게 전환한다")
        @Test
        fun `handles month boundary correctly`() {
            // given
            val bucketKey = "ranking:products:daily:20250201"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:daily:20250131")
        }

        @DisplayName("연도 경계에서 이전 연도로 올바르게 전환한다")
        @Test
        fun `handles year boundary correctly`() {
            // given
            val bucketKey = "ranking:products:daily:20250101"

            // when
            val previousKey = rankingKeyGenerator.previousBucketKey(bucketKey)

            // then
            assertThat(previousKey).isEqualTo("ranking:products:daily:20241231")
        }

        @DisplayName("잘못된 형식의 버킷 키에 대해 예외를 던진다")
        @Test
        fun `throws exception for invalid bucket key format`() {
            // given
            val invalidKey = "invalid:key"

            // when & then
            assertThrows<IllegalArgumentException> {
                rankingKeyGenerator.previousBucketKey(invalidKey)
            }
        }
    }
}
