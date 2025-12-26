package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    @DisplayName("bucketKey 메서드 테스트")
    @Nested
    inner class BucketKey {

        @DisplayName("Instant에서 ranking:hourly:yyyyMMddHH 형식의 키를 생성한다")
        @Test
        fun `generates key in ranking hourly yyyyMMddHH format`() {
            // given
            val instant = ZonedDateTime.of(2025, 1, 15, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()

            // when
            val key = RankingKeyGenerator.bucketKey(instant)

            // then
            assertThat(key).isEqualTo("ranking:hourly:2025011514")
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
            assertThat(key).isEqualTo("ranking:hourly:2025123123")
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
            assertThat(key).isEqualTo("ranking:hourly:2025011514")
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
            assertThat(key).isEqualTo("ranking:hourly:2025010100")
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
            assertThat(key).isEqualTo("ranking:hourly:2025011500")
        }
    }

    @DisplayName("currentBucketKey 메서드 테스트")
    @Nested
    inner class CurrentBucketKey {

        @DisplayName("현재 시간 기준으로 ranking:hourly: prefix를 가진 키를 생성한다")
        @Test
        fun `generates key with ranking hourly prefix`() {
            // when
            val key = RankingKeyGenerator.currentBucketKey()

            // then
            assertThat(key).startsWith("ranking:hourly:")
        }

        @DisplayName("현재 시간 기준으로 10자리 날짜시간 문자열을 포함한 키를 생성한다")
        @Test
        fun `generates key with 10-digit datetime string`() {
            // when
            val key = RankingKeyGenerator.currentBucketKey()

            // then
            val dateTimePart = key.removePrefix("ranking:hourly:")
            assertThat(dateTimePart).matches("\\d{10}")
        }
    }
}
