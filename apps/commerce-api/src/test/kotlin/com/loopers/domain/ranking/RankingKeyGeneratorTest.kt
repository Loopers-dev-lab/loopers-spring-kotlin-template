package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    private lateinit var rankingKeyGenerator: RankingKeyGenerator

    @BeforeEach
    fun setUp() {
        rankingKeyGenerator = RankingKeyGenerator()
    }

    @DisplayName("bucketKey(period, dateTime) 메서드 테스트")
    @Nested
    inner class BucketKeyWithPeriodAndDateTime {

        @DisplayName("HOURLY period에서 ranking:products:hourly:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates hourly key format`() {
            // given
            val dateTime = ZonedDateTime.of(2025, 1, 26, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, dateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("DAILY period에서 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates daily key format`() {
            // given
            val dateTime = ZonedDateTime.of(2025, 1, 26, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, dateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250126")
        }

        @DisplayName("UTC 시간을 Asia/Seoul 타임존으로 변환하여 키를 생성한다")
        @Test
        fun `converts UTC to Asia Seoul timezone`() {
            // given
            // UTC 2025-01-26 05:30:00 = KST 2025-01-26 14:30:00
            val utcDateTime = ZonedDateTime.of(2025, 1, 26, 5, 30, 0, 0, ZoneId.of("UTC"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, utcDateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025012614")
        }

        @DisplayName("시간이 정시가 아니어도 정시로 truncate하여 키를 생성한다")
        @Test
        fun `truncates to hour boundary`() {
            // given
            val dateTime = ZonedDateTime.of(2025, 12, 31, 23, 59, 59, 999_999_999, ZoneId.of("Asia/Seoul"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, dateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025123123")
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
    }

    @DisplayName("currentBucketKey(period) 메서드 테스트")
    @Nested
    inner class CurrentBucketKeyWithPeriod {

        @DisplayName("HOURLY period에서 현재 시간 기준으로 ranking:products:hourly: prefix를 가진 키를 생성한다")
        @Test
        fun `generates hourly key with prefix`() {
            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)

            // then
            assertThat(key).startsWith("ranking:products:hourly:")
            val dateTimePart = key.removePrefix("ranking:products:hourly:")
            assertThat(dateTimePart).matches("\\d{10}")
        }

        @DisplayName("DAILY period에서 현재 시간 기준으로 ranking:products:daily: prefix를 가진 키를 생성한다")
        @Test
        fun `generates daily key with prefix`() {
            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)

            // then
            assertThat(key).startsWith("ranking:products:daily:")
            val datePart = key.removePrefix("ranking:products:daily:")
            assertThat(datePart).matches("\\d{8}")
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
