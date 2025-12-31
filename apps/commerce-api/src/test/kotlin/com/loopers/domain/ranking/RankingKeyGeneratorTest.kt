package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    @DisplayName("bucketKey 메서드 테스트")
    @Nested
    inner class BucketKey {

        @DisplayName("Instant에서 ranking:products:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates key in ranking hourly yyyyMMddHH format`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = RankingKeyGenerator.bucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011514")
        }

        @DisplayName("시간이 정시가 아니어도 정시로 truncate하여 키를 생성한다")
        @Test
        fun `truncates to hour boundary`() {
            // given
            val instant = ZonedDateTime.of(2025, 12, 31, 23, 59, 59, 999_999_999, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = RankingKeyGenerator.bucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025123123")
        }

        @DisplayName("UTC 시간을 Asia/Seoul 타임존으로 변환하여 키를 생성한다")
        @Test
        fun `converts UTC to Asia Seoul timezone`() {
            // given
            // UTC 2025-01-15 05:30:00 = KST 2025-01-15 14:30:00 (Asia/Seoul is UTC+9)
            val utcInstant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = RankingKeyGenerator.bucketKey(utcInstant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011514")
        }

        @DisplayName("자정 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles midnight boundary correctly`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = RankingKeyGenerator.bucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025010100")
        }

        @DisplayName("일자 변경 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles date change boundary correctly`() {
            // given
            // UTC 2025-01-14 15:30:00 = KST 2025-01-15 00:30:00
            val utcInstant = Instant.parse("2025-01-14T15:30:00Z")

            // when
            val key = RankingKeyGenerator.bucketKey(utcInstant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011500")
        }
    }

    @DisplayName("currentBucketKey 메서드 테스트")
    @Nested
    inner class CurrentBucketKey {

        @DisplayName("현재 시간 기준으로 ranking:products: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking hourly prefix`() {
            // when
            val key = RankingKeyGenerator.currentBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:")
        }

        @DisplayName("현재 시간 기준으로 10자리 날짜시간 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 10-digit datetime string`() {
            // when
            val key = RankingKeyGenerator.currentBucketKey()

            // then
            val dateTimePart = key.removePrefix("ranking:products:")
            assertThat(dateTimePart).matches("\\d{10}")
        }
    }

    @DisplayName("previousBucketKey 메서드 테스트")
    @Nested
    inner class PreviousBucketKey {

        @DisplayName("이전 시간 기준으로 ranking:products: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking products prefix`() {
            // when
            val key = RankingKeyGenerator.previousBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:")
        }

        @DisplayName("현재 시간보다 1시간 이전의 키를 생성한다")
        @Test
        fun `generates key for previous hour`() {
            // when
            val currentKey = RankingKeyGenerator.currentBucketKey()
            val previousKey = RankingKeyGenerator.previousBucketKey()

            // then
            assertThat(currentKey).isNotEqualTo(previousKey)
            // Both should have 10-digit datetime format
            assertThat(currentKey.removePrefix("ranking:products:")).matches("\\d{10}")
            assertThat(previousKey.removePrefix("ranking:products:")).matches("\\d{10}")
        }
    }

    @DisplayName("dailyBucketKey 메서드 테스트")
    @Nested
    inner class DailyBucketKey {

        @DisplayName("LocalDate에서 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates key in ranking daily yyyyMMdd format`() {
            // given
            val date = LocalDate.of(2025, 1, 15)

            // when
            val key = RankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }

        @DisplayName("연말 날짜에서 올바른 키를 생성한다")
        @Test
        fun `generates key for year end date`() {
            // given
            val date = LocalDate.of(2025, 12, 31)

            // when
            val key = RankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20251231")
        }

        @DisplayName("연초 날짜에서 올바른 키를 생성한다")
        @Test
        fun `generates key for year start date`() {
            // given
            val date = LocalDate.of(2025, 1, 1)

            // when
            val key = RankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250101")
        }
    }

    @DisplayName("currentDailyBucketKey 메서드 테스트")
    @Nested
    inner class CurrentDailyBucketKey {

        @DisplayName("오늘 날짜 기준으로 ranking:products:daily: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking daily prefix`() {
            // when
            val key = RankingKeyGenerator.currentDailyBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:daily:")
        }

        @DisplayName("오늘 날짜 기준으로 8자리 날짜 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 8-digit date string`() {
            // when
            val key = RankingKeyGenerator.currentDailyBucketKey()

            // then
            val datePart = key.removePrefix("ranking:products:daily:")
            assertThat(datePart).matches("\\d{8}")
        }
    }

    @DisplayName("previousDailyBucketKey 메서드 테스트")
    @Nested
    inner class PreviousDailyBucketKey {

        @DisplayName("어제 날짜 기준으로 ranking:products:daily: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking daily prefix`() {
            // when
            val key = RankingKeyGenerator.previousDailyBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:daily:")
        }

        @DisplayName("오늘 키와 다른 키를 생성한다")
        @Test
        fun `generates different key from current daily key`() {
            // when
            val currentKey = RankingKeyGenerator.currentDailyBucketKey()
            val previousKey = RankingKeyGenerator.previousDailyBucketKey()

            // then
            assertThat(currentKey).isNotEqualTo(previousKey)
        }

        @DisplayName("어제 날짜 기준으로 8자리 날짜 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 8-digit date string`() {
            // when
            val key = RankingKeyGenerator.previousDailyBucketKey()

            // then
            val datePart = key.removePrefix("ranking:products:daily:")
            assertThat(datePart).matches("\\d{8}")
        }
    }
}
