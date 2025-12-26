package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("AggregationKey 단위 테스트")
class AggregationKeyTest {

    @DisplayName("생성자 테스트")
    @Nested
    inner class Constructor {

        @DisplayName("productId와 hourBucket으로 AggregationKey를 생성할 수 있다")
        @Test
        fun `creates AggregationKey with productId and hourBucket`() {
            // given
            val productId = 1L
            val hourBucket = Instant.parse("2025-01-15T14:00:00Z")

            // when
            val key = AggregationKey(
                productId = productId,
                hourBucket = hourBucket,
            )

            // then
            assertThat(key.productId).isEqualTo(productId)
            assertThat(key.hourBucket).isEqualTo(hourBucket)
        }

        @DisplayName("동일한 productId와 hourBucket을 가진 AggregationKey는 동등하다")
        @Test
        fun `AggregationKeys with same productId and hourBucket are equal`() {
            // given
            val key1 = AggregationKey(
                productId = 1L,
                hourBucket = Instant.parse("2025-01-15T14:00:00Z"),
            )
            val key2 = AggregationKey(
                productId = 1L,
                hourBucket = Instant.parse("2025-01-15T14:00:00Z"),
            )

            // when & then
            assertThat(key1).isEqualTo(key2)
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode())
        }

        @DisplayName("다른 productId를 가진 AggregationKey는 동등하지 않다")
        @Test
        fun `AggregationKeys with different productId are not equal`() {
            // given
            val key1 = AggregationKey(
                productId = 1L,
                hourBucket = Instant.parse("2025-01-15T14:00:00Z"),
            )
            val key2 = AggregationKey(
                productId = 2L,
                hourBucket = Instant.parse("2025-01-15T14:00:00Z"),
            )

            // when & then
            assertThat(key1).isNotEqualTo(key2)
        }

        @DisplayName("다른 hourBucket을 가진 AggregationKey는 동등하지 않다")
        @Test
        fun `AggregationKeys with different hourBucket are not equal`() {
            // given
            val key1 = AggregationKey(
                productId = 1L,
                hourBucket = Instant.parse("2025-01-15T14:00:00Z"),
            )
            val key2 = AggregationKey(
                productId = 1L,
                hourBucket = Instant.parse("2025-01-15T15:00:00Z"),
            )

            // when & then
            assertThat(key1).isNotEqualTo(key2)
        }
    }

    @DisplayName("from 팩토리 메서드 테스트")
    @Nested
    inner class From {

        @DisplayName("AccumulateMetricCommand.Item으로부터 AggregationKey를 생성한다")
        @Test
        fun `creates AggregationKey from AccumulateMetricCommand Item`() {
            // given
            val item = AccumulateMetricCommand.Item(
                productId = 100L,
                metricType = MetricType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T14:30:45Z"),
            )

            // when
            val key = AggregationKey.from(item)

            // then
            assertThat(key.productId).isEqualTo(100L)
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }

        @DisplayName("항목의 occurredAt이 시간 단위로 truncate된다")
        @Test
        fun `truncates occurredAt to hour`() {
            // given
            val item = AccumulateMetricCommand.Item(
                productId = 1L,
                metricType = MetricType.LIKE_CREATED,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T14:59:59.999Z"),
            )

            // when
            val key = AggregationKey.from(item)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }

        @DisplayName("정각 시간의 항목은 해당 시간 버킷에 포함된다")
        @Test
        fun `item at exact hour belongs to that hour bucket`() {
            // given
            val item = AccumulateMetricCommand.Item(
                productId = 1L,
                metricType = MetricType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T15:00:00Z"),
            )

            // when
            val key = AggregationKey.from(item)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T15:00:00Z"))
        }
    }

    @DisplayName("of 팩토리 메서드 테스트")
    @Nested
    inner class Of {

        @DisplayName("productId와 occurredAt으로 AggregationKey를 생성한다")
        @Test
        fun `creates AggregationKey from productId and occurredAt`() {
            // given
            val productId = 50L
            val occurredAt = Instant.parse("2025-01-15T14:45:30Z")

            // when
            val key = AggregationKey.of(productId, occurredAt)

            // then
            assertThat(key.productId).isEqualTo(50L)
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }

        @DisplayName("자정 직전 이벤트는 해당 날짜의 23시 버킷에 포함된다")
        @Test
        fun `event just before midnight belongs to 23 hour bucket`() {
            // given
            val productId = 1L
            val occurredAt = Instant.parse("2025-01-15T23:59:59Z")

            // when
            val key = AggregationKey.of(productId, occurredAt)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T23:00:00Z"))
        }

        @DisplayName("자정 이벤트는 다음 날짜의 0시 버킷에 포함된다")
        @Test
        fun `event at midnight belongs to next day 0 hour bucket`() {
            // given
            val productId = 1L
            val occurredAt = Instant.parse("2025-01-16T00:00:00Z")

            // when
            val key = AggregationKey.of(productId, occurredAt)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-16T00:00:00Z"))
        }
    }

    @DisplayName("hourBucket truncation 테스트")
    @Nested
    inner class HourBucketTruncation {

        @DisplayName("14:59:58 항목이 15:00:02에 flush되어도 14시 버킷에 집계된다")
        @Test
        fun `item at 14_59_58 flushed at 15_00_02 goes to 14h bucket`() {
            // given - 14:59:58에 발생한 항목
            val itemOccurredAt = Instant.parse("2025-01-15T14:59:58Z")
            val item = AccumulateMetricCommand.Item(
                productId = 1L,
                metricType = MetricType.VIEW,
                orderAmount = null,
                occurredAt = itemOccurredAt,
            )

            // when - flush 시점(15:00:02)과 무관하게 occurredAt 기준으로 버킷 결정
            val key = AggregationKey.from(item)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }

        @DisplayName("밀리초, 나노초가 있는 시간도 올바르게 truncate된다")
        @Test
        fun `truncates milliseconds and nanoseconds correctly`() {
            // given
            val occurredAt = Instant.parse("2025-01-15T14:30:45.123456789Z")

            // when
            val key = AggregationKey.of(1L, occurredAt)

            // then
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }

        @DisplayName("KST 기준으로 시간 버킷이 올바르게 결정된다")
        @Test
        fun `hour bucket is correctly determined based on KST`() {
            // given - KST 2025-01-15 23:30:00 = UTC 2025-01-15 14:30:00
            val kstDateTime = ZonedDateTime.of(2025, 1, 15, 23, 30, 0, 0, ZoneId.of("Asia/Seoul"))
            val occurredAt = kstDateTime.toInstant()

            // when
            val key = AggregationKey.of(1L, occurredAt)

            // then - UTC 기준으로 truncate되므로 14:00:00 UTC
            assertThat(key.hourBucket).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }
    }

    @DisplayName("HashMap 키로 사용 테스트")
    @Nested
    inner class HashMapKey {

        @DisplayName("AggregationKey를 HashMap의 키로 사용할 수 있다")
        @Test
        fun `can use AggregationKey as HashMap key`() {
            // given
            val map = HashMap<AggregationKey, Int>()
            val key1 = AggregationKey(1L, Instant.parse("2025-01-15T14:00:00Z"))
            val key2 = AggregationKey(1L, Instant.parse("2025-01-15T14:00:00Z"))

            // when
            map[key1] = 100
            val value = map[key2]

            // then
            assertThat(value).isEqualTo(100)
        }

        @DisplayName("동일한 key로 값을 업데이트할 수 있다")
        @Test
        fun `can update value with same key`() {
            // given
            val map = HashMap<AggregationKey, Int>()
            val key = AggregationKey(1L, Instant.parse("2025-01-15T14:00:00Z"))
            map[key] = 100

            // when
            map[key] = 200

            // then
            assertThat(map[key]).isEqualTo(200)
            assertThat(map.size).isEqualTo(1)
        }

        @DisplayName("다른 productId는 다른 key로 취급된다")
        @Test
        fun `different productIds are treated as different keys`() {
            // given
            val map = HashMap<AggregationKey, Int>()
            val key1 = AggregationKey(1L, Instant.parse("2025-01-15T14:00:00Z"))
            val key2 = AggregationKey(2L, Instant.parse("2025-01-15T14:00:00Z"))

            // when
            map[key1] = 100
            map[key2] = 200

            // then
            assertThat(map.size).isEqualTo(2)
            assertThat(map[key1]).isEqualTo(100)
            assertThat(map[key2]).isEqualTo(200)
        }

        @DisplayName("다른 hourBucket은 다른 key로 취급된다")
        @Test
        fun `different hourBuckets are treated as different keys`() {
            // given
            val map = HashMap<AggregationKey, Int>()
            val key1 = AggregationKey(1L, Instant.parse("2025-01-15T14:00:00Z"))
            val key2 = AggregationKey(1L, Instant.parse("2025-01-15T15:00:00Z"))

            // when
            map[key1] = 100
            map[key2] = 200

            // then
            assertThat(map.size).isEqualTo(2)
            assertThat(map[key1]).isEqualTo(100)
            assertThat(map[key2]).isEqualTo(200)
        }
    }
}
