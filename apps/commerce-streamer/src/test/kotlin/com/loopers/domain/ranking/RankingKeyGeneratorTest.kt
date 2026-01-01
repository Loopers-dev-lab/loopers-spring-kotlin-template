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

    private val rankingKeyGenerator = RankingKeyGenerator()

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
            val key = rankingKeyGenerator.bucketKey(instant)

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
            val key = rankingKeyGenerator.bucketKey(instant)

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
            val key = rankingKeyGenerator.bucketKey(utcInstant)

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
            val key = rankingKeyGenerator.bucketKey(instant)

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
            val key = rankingKeyGenerator.bucketKey(utcInstant)

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
            val key = rankingKeyGenerator.currentBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:")
        }

        @DisplayName("현재 시간 기준으로 10자리 날짜시간 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 10-digit datetime string`() {
            // when
            val key = rankingKeyGenerator.currentBucketKey()

            // then
            val dateTimePart = key.removePrefix("ranking:products:")
            assertThat(dateTimePart).matches("\\d{10}")
        }
    }

    @DisplayName("previousBucketKey 메서드 테스트")
    @Nested
    inner class PreviousBucketKey {

        @DisplayName("현재 시간 기준 이전 시간의 버킷 키를 생성한다")
        @Test
        fun `generates previous hour bucket key`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.previousBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011513")
        }

        @DisplayName("자정 경계에서 이전 시간 키를 올바르게 생성한다")
        @Test
        fun `handles midnight boundary correctly for previous bucket`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 15, 0, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.previousBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011423")
        }

        @DisplayName("연도 경계에서 이전 시간 키를 올바르게 생성한다")
        @Test
        fun `handles year boundary correctly for previous bucket`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.previousBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2024123123")
        }

        @DisplayName("UTC 시간을 Asia/Seoul 타임존으로 변환하여 이전 시간 키를 생성한다")
        @Test
        fun `converts UTC to Asia Seoul timezone for previous bucket`() {
            // given
            // UTC 2025-01-15 05:30:00 = KST 2025-01-15 14:30:00, so previous = KST 13:00
            val utcInstant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = rankingKeyGenerator.previousBucketKey(utcInstant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011513")
        }

        @DisplayName("previousBucketKey()는 현재 시간 기준 이전 시간의 버킷 키를 생성한다")
        @Test
        fun `previousBucketKey without argument generates previous hour key`() {
            // when
            val currentKey = rankingKeyGenerator.currentBucketKey()
            val previousKey = rankingKeyGenerator.previousBucketKey()

            // then
            assertThat(previousKey).startsWith("ranking:products:")
            val currentDateTimePart = currentKey.removePrefix("ranking:products:")
            val previousDateTimePart = previousKey.removePrefix("ranking:products:")
            assertThat(previousDateTimePart).matches("\\d{10}")

            // previousKey should be 1 hour before currentKey
            val currentHour = currentDateTimePart.takeLast(2).toInt()
            val previousHour = previousDateTimePart.takeLast(2).toInt()

            // Handle hour boundary (e.g., currentHour=0, previousHour=23)
            if (currentHour == 0) {
                assertThat(previousHour).isEqualTo(23)
            } else {
                assertThat(previousHour).isEqualTo(currentHour - 1)
            }
        }
    }

    @DisplayName("nextBucketKey 메서드 테스트")
    @Nested
    inner class NextBucketKey {

        @DisplayName("현재 시간 기준 다음 시간의 버킷 키를 생성한다")
        @Test
        fun `generates next hour bucket key`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.nextBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011515")
        }

        @DisplayName("자정 경계에서 다음 시간 키를 올바르게 생성한다")
        @Test
        fun `handles midnight boundary correctly for next bucket`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 14, 23, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.nextBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011500")
        }

        @DisplayName("연도 경계에서 다음 시간 키를 올바르게 생성한다")
        @Test
        fun `handles year boundary correctly for next bucket`() {
            // given
            val instant = ZonedDateTime.of(2025, 12, 31, 23, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = rankingKeyGenerator.nextBucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:products:2026010100")
        }

        @DisplayName("UTC 시간을 Asia/Seoul 타임존으로 변환하여 다음 시간 키를 생성한다")
        @Test
        fun `converts UTC to Asia Seoul timezone for next bucket`() {
            // given
            // UTC 2025-01-15 05:30:00 = KST 2025-01-15 14:30:00, so next = KST 15:00
            val utcInstant = Instant.parse("2025-01-15T05:30:00Z")

            // when
            val key = rankingKeyGenerator.nextBucketKey(utcInstant)

            // then
            assertThat(key).isEqualTo("ranking:products:2025011515")
        }

        @DisplayName("nextBucketKey()는 현재 시간 기준 다음 시간의 버킷 키를 생성한다")
        @Test
        fun `nextBucketKey without argument generates next hour key`() {
            // when
            val currentKey = rankingKeyGenerator.currentBucketKey()
            val nextKey = rankingKeyGenerator.nextBucketKey()

            // then
            assertThat(nextKey).startsWith("ranking:products:")
            val currentDateTimePart = currentKey.removePrefix("ranking:products:")
            val nextDateTimePart = nextKey.removePrefix("ranking:products:")
            assertThat(nextDateTimePart).matches("\\d{10}")

            // nextKey should be 1 hour after currentKey
            val currentHour = currentDateTimePart.takeLast(2).toInt()
            val nextHour = nextDateTimePart.takeLast(2).toInt()

            // Handle hour boundary (e.g., currentHour=23, nextHour=0)
            if (currentHour == 23) {
                assertThat(nextHour).isEqualTo(0)
            } else {
                assertThat(nextHour).isEqualTo(currentHour + 1)
            }
        }
    }

    @DisplayName("dailyBucketKey 메서드 테스트")
    @Nested
    inner class DailyBucketKey {

        @DisplayName("LocalDate에서 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates daily key in ranking daily yyyyMMdd format`() {
            // given
            val date = LocalDate.of(2025, 1, 15)

            // when
            val key = rankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }

        @DisplayName("연도 시작 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles year start boundary correctly`() {
            // given
            val date = LocalDate.of(2025, 1, 1)

            // when
            val key = rankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250101")
        }

        @DisplayName("연도 끝 경계에서 올바른 키를 생성한다")
        @Test
        fun `handles year end boundary correctly`() {
            // given
            val date = LocalDate.of(2025, 12, 31)

            // when
            val key = rankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20251231")
        }

        @DisplayName("2월 말일에 올바른 키를 생성한다")
        @Test
        fun `handles february end correctly`() {
            // given
            val date = LocalDate.of(2025, 2, 28) // 2025 is not a leap year

            // when
            val key = rankingKeyGenerator.dailyBucketKey(date)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250228")
        }
    }

    @DisplayName("currentDailyBucketKey 메서드 테스트")
    @Nested
    inner class CurrentDailyBucketKey {

        @DisplayName("현재 날짜 기준으로 ranking:products:daily: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking daily prefix`() {
            // when
            val key = rankingKeyGenerator.currentDailyBucketKey()

            // then
            assertThat(key).startsWith("ranking:products:daily:")
        }

        @DisplayName("현재 날짜 기준으로 8자리 날짜 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 8-digit date string`() {
            // when
            val key = rankingKeyGenerator.currentDailyBucketKey()

            // then
            val datePart = key.removePrefix("ranking:products:daily:")
            assertThat(datePart).matches("\\d{8}")
        }

        @DisplayName("현재 날짜의 키와 일치한다")
        @Test
        fun `matches current date key`() {
            // given
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

            // when
            val currentDailyKey = rankingKeyGenerator.currentDailyBucketKey()
            val expectedKey = rankingKeyGenerator.dailyBucketKey(today)

            // then
            assertThat(currentDailyKey).isEqualTo(expectedKey)
        }
    }

    @DisplayName("period-aware bucketKey 메서드 테스트")
    @Nested
    inner class PeriodAwareBucketKey {

        @DisplayName("HOURLY period로 ranking:products:hourly:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates hourly key with period`() {
            // given
            val dateTime = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, dateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:hourly:2025011514")
        }

        @DisplayName("DAILY period로 ranking:products:daily:yyyyMMdd 형식의 키를 생성한다")
        @Test
        fun `generates daily key with period`() {
            // given
            val dateTime = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

            // when
            val key = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, dateTime)

            // then
            assertThat(key).isEqualTo("ranking:products:daily:20250115")
        }
    }

    @DisplayName("currentBucketKey(period) 메서드 테스트")
    @Nested
    inner class PeriodAwareCurrentBucketKey {

        @DisplayName("HOURLY period로 현재 시간 기준 hourly 키를 생성한다")
        @Test
        fun `generates current hourly key with period`() {
            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)

            // then
            assertThat(key).startsWith("ranking:products:hourly:")
            val dateTimePart = key.removePrefix("ranking:products:hourly:")
            assertThat(dateTimePart).matches("\\d{10}")
        }

        @DisplayName("DAILY period로 현재 날짜 기준 daily 키를 생성한다")
        @Test
        fun `generates current daily key with period`() {
            // when
            val key = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)

            // then
            assertThat(key).startsWith("ranking:products:daily:")
            val datePart = key.removePrefix("ranking:products:daily:")
            assertThat(datePart).matches("\\d{8}")
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
