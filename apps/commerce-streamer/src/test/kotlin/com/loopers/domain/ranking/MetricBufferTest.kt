package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("MetricBuffer 단위 테스트")
class MetricBufferTest {

    private val baseTime = Instant.parse("2024-01-01T10:00:00Z")

    private fun createKey(productId: Long): AggregationKey {
        return AggregationKey.of(productId, baseTime)
    }

    @DisplayName("초기 상태 테스트")
    @Nested
    inner class InitialState {

        @DisplayName("생성 시 버퍼가 비어있다")
        @Test
        fun `buffer is empty on creation`() {
            // given
            val buffer = MetricBuffer()

            // then
            assertThat(buffer.isEmpty()).isTrue()
            assertThat(buffer.size()).isEqualTo(0)
        }

        @DisplayName("빈 버퍼를 poll하면 빈 Map을 반환한다")
        @Test
        fun `poll on empty buffer returns empty map`() {
            // given
            val buffer = MetricBuffer()

            // when
            val result = buffer.poll()

            // then
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("accumulate 메서드 테스트")
    @Nested
    inner class Accumulate {

        @DisplayName("메트릭을 축적하면 버퍼에 추가된다")
        @Test
        fun `accumulate adds metrics to buffer`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)

            // when
            buffer.accumulate(key) {
                increment(MetricType.VIEW)
            }

            // then
            assertThat(buffer.isEmpty()).isFalse()
            assertThat(buffer.size()).isEqualTo(1)
        }

        @DisplayName("동일 키에 여러 번 축적하면 카운트가 누적된다")
        @Test
        fun `accumulate same key accumulates counts`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)

            // when
            repeat(5) {
                buffer.accumulate(key) {
                    increment(MetricType.VIEW)
                }
            }

            // then
            assertThat(buffer.size()).isEqualTo(1)
            val snapshot = buffer.snapshot()
            assertThat(snapshot[key]?.getViews()).isEqualTo(5L)
        }

        @DisplayName("서로 다른 키는 별도로 축적된다")
        @Test
        fun `different keys are accumulated separately`() {
            // given
            val buffer = MetricBuffer()
            val key1 = createKey(1L)
            val key2 = createKey(2L)

            // when
            buffer.accumulate(key1) { increment(MetricType.VIEW) }
            buffer.accumulate(key2) { increment(MetricType.VIEW) }
            buffer.accumulate(key1) { increment(MetricType.VIEW) }

            // then
            assertThat(buffer.size()).isEqualTo(2)
            val snapshot = buffer.snapshot()
            assertThat(snapshot[key1]?.getViews()).isEqualTo(2L)
            assertThat(snapshot[key2]?.getViews()).isEqualTo(1L)
        }

        @DisplayName("여러 메트릭 타입을 한 번에 축적할 수 있다")
        @Test
        fun `can accumulate multiple metric types at once`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)

            // when
            buffer.accumulate(key) {
                increment(MetricType.VIEW)
                increment(MetricType.LIKE_CREATED)
                increment(MetricType.ORDER_PAID)
                addOrderAmount(BigDecimal("100.00"))
            }

            // then
            val snapshot = buffer.snapshot()
            val counts = snapshot[key]!!
            assertThat(counts.getViews()).isEqualTo(1L)
            assertThat(counts.getLikes()).isEqualTo(1L)
            assertThat(counts.getOrderCount()).isEqualTo(1L)
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal("100.00"))
        }
    }

    @DisplayName("poll 메서드 테스트")
    @Nested
    inner class Poll {

        @DisplayName("poll은 데이터를 반환하고 버퍼를 비운다")
        @Test
        fun `poll returns data and clears buffer`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            buffer.accumulate(key) { increment(MetricType.VIEW) }

            // when
            val result = buffer.poll()

            // then
            assertThat(result).hasSize(1)
            assertThat(result[key]?.getViews()).isEqualTo(1L)
            assertThat(buffer.isEmpty()).isTrue()
        }

        @DisplayName("poll 후 새로운 축적은 새 버퍼에 기록된다")
        @Test
        fun `accumulate after poll goes to new buffer`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            buffer.accumulate(key) { increment(MetricType.VIEW) }

            // when
            val firstPoll = buffer.poll()
            buffer.accumulate(key) { increment(MetricType.LIKE_CREATED) }
            val secondPoll = buffer.poll()

            // then
            assertThat(firstPoll[key]?.getViews()).isEqualTo(1L)
            assertThat(firstPoll[key]?.getLikes()).isEqualTo(0L)

            assertThat(secondPoll[key]?.getViews()).isEqualTo(0L)
            assertThat(secondPoll[key]?.getLikes()).isEqualTo(1L)
        }

        @DisplayName("연속 poll은 빈 결과를 반환한다")
        @Test
        fun `consecutive polls return empty results`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            buffer.accumulate(key) { increment(MetricType.VIEW) }

            // when
            val firstPoll = buffer.poll()
            val secondPoll = buffer.poll()

            // then
            assertThat(firstPoll).hasSize(1)
            assertThat(secondPoll).isEmpty()
        }
    }

    @DisplayName("snapshot 메서드 테스트")
    @Nested
    inner class Snapshot {

        @DisplayName("snapshot은 버퍼를 비우지 않는다")
        @Test
        fun `snapshot does not clear buffer`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            buffer.accumulate(key) { increment(MetricType.VIEW) }

            // when
            val snapshot1 = buffer.snapshot()
            val snapshot2 = buffer.snapshot()

            // then
            assertThat(snapshot1).hasSize(1)
            assertThat(snapshot2).hasSize(1)
            assertThat(buffer.isEmpty()).isFalse()
        }

        @DisplayName("snapshot 후에도 poll이 데이터를 반환한다")
        @Test
        fun `poll after snapshot still returns data`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            buffer.accumulate(key) { increment(MetricType.VIEW) }

            // when
            buffer.snapshot()
            val polled = buffer.poll()

            // then
            assertThat(polled).hasSize(1)
        }
    }

    @DisplayName("size와 isEmpty 테스트")
    @Nested
    inner class SizeAndIsEmpty {

        @DisplayName("size는 현재 항목 수를 반환한다")
        @Test
        fun `size returns current entry count`() {
            // given
            val buffer = MetricBuffer()

            // when & then
            assertThat(buffer.size()).isEqualTo(0)

            buffer.accumulate(createKey(1L)) { increment(MetricType.VIEW) }
            assertThat(buffer.size()).isEqualTo(1)

            buffer.accumulate(createKey(2L)) { increment(MetricType.VIEW) }
            assertThat(buffer.size()).isEqualTo(2)

            // 동일 키는 카운트 증가 안함
            buffer.accumulate(createKey(1L)) { increment(MetricType.VIEW) }
            assertThat(buffer.size()).isEqualTo(2)
        }

        @DisplayName("isEmpty는 버퍼 상태를 정확히 반영한다")
        @Test
        fun `isEmpty reflects buffer state correctly`() {
            // given
            val buffer = MetricBuffer()

            // then
            assertThat(buffer.isEmpty()).isTrue()

            buffer.accumulate(createKey(1L)) { increment(MetricType.VIEW) }
            assertThat(buffer.isEmpty()).isFalse()

            buffer.poll()
            assertThat(buffer.isEmpty()).isTrue()
        }
    }

    @DisplayName("스레드 안전성 테스트")
    @Nested
    inner class ThreadSafety {

        @DisplayName("동시에 여러 스레드에서 축적해도 데이터 유실이 없다")
        @Test
        fun `concurrent accumulates do not lose data`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            val threadCount = 10
            val incrementsPerThread = 1000
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(incrementsPerThread) {
                            buffer.accumulate(key) {
                                increment(MetricType.VIEW)
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // then
            val result = buffer.poll()
            assertThat(result[key]?.getViews())
                .isEqualTo((threadCount * incrementsPerThread).toLong())
        }

        @DisplayName("poll의 원자성 - getAndSet은 데이터를 잃지 않는다")
        @Test
        fun `poll atomicity - getAndSet does not lose data`() {
            // given
            val buffer = MetricBuffer()
            val key = createKey(1L)
            val accumulateCount = 10000

            // 먼저 데이터를 축적
            repeat(accumulateCount) {
                buffer.accumulate(key) {
                    increment(MetricType.VIEW)
                }
            }

            // when - poll과 accumulate를 동시에 수행
            val pollResult = buffer.poll()

            // poll 직후 새로운 축적
            buffer.accumulate(key) {
                increment(MetricType.LIKE_CREATED)
            }

            val secondPoll = buffer.poll()

            // then
            // 첫 번째 poll은 이전 데이터를 모두 가져옴
            assertThat(pollResult[key]?.getViews()).isEqualTo(accumulateCount.toLong())

            // 두 번째 poll은 새로운 데이터만 가져옴 (원자적 스왑 증명)
            assertThat(secondPoll[key]?.getViews()).isEqualTo(0L)
            assertThat(secondPoll[key]?.getLikes()).isEqualTo(1L)
        }

        @DisplayName("여러 키에 대한 동시 축적이 정확하게 처리된다")
        @Test
        fun `concurrent accumulates to different keys are accurate`() {
            // given
            val buffer = MetricBuffer()
            val keyCount = 100
            val threadCount = 10
            val incrementsPerKey = 100
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when - 각 스레드가 모든 키에 대해 증가
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(keyCount) { keyId ->
                            repeat(incrementsPerKey) {
                                buffer.accumulate(createKey(keyId.toLong())) {
                                    increment(MetricType.VIEW)
                                }
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // then
            val result = buffer.poll()
            assertThat(result).hasSize(keyCount)

            val expectedPerKey = (threadCount * incrementsPerKey).toLong()
            result.values.forEach { counts ->
                assertThat(counts.getViews()).isEqualTo(expectedPerKey)
            }
        }
    }
}
