package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("MutableCounts 단위 테스트")
class MutableCountsTest {

    @DisplayName("초기 상태 테스트")
    @Nested
    inner class InitialState {

        @DisplayName("생성 시 모든 카운트가 0이다")
        @Test
        fun `all counts are zero on creation`() {
            // when
            val counts = MutableCounts()

            // then
            assertThat(counts.getViews()).isEqualTo(0L)
            assertThat(counts.getLikes()).isEqualTo(0L)
            assertThat(counts.getOrderCount()).isEqualTo(0L)
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("생성 시 스냅샷의 모든 값이 0이다")
        @Test
        fun `snapshot has all zeros on creation`() {
            // given
            val counts = MutableCounts()

            // when
            val snapshot = counts.toSnapshot()

            // then
            assertThat(snapshot.views).isEqualTo(0L)
            assertThat(snapshot.likes).isEqualTo(0L)
            assertThat(snapshot.orderCount).isEqualTo(0L)
            assertThat(snapshot.orderAmount).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @DisplayName("increment 메서드 테스트")
    @Nested
    inner class Increment {

        @DisplayName("VIEW 메트릭 타입은 views를 증가시킨다")
        @Test
        fun `VIEW metric type increments views`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.VIEW)

            // then
            assertThat(counts.getViews()).isEqualTo(1L)
            assertThat(counts.getLikes()).isEqualTo(0L)
            assertThat(counts.getOrderCount()).isEqualTo(0L)
        }

        @DisplayName("LIKE_CREATED 메트릭 타입은 likes를 증가시킨다")
        @Test
        fun `LIKE_CREATED metric type increments likes`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.LIKE_CREATED)

            // then
            assertThat(counts.getLikes()).isEqualTo(1L)
            assertThat(counts.getViews()).isEqualTo(0L)
            assertThat(counts.getOrderCount()).isEqualTo(0L)
        }

        @DisplayName("LIKE_CANCELED 메트릭 타입은 likes를 감소시킨다")
        @Test
        fun `LIKE_CANCELED metric type decrements likes`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.LIKE_CANCELED)

            // then
            assertThat(counts.getLikes()).isEqualTo(-1L)
        }

        @DisplayName("ORDER_PAID 메트릭 타입은 orderCount를 증가시킨다")
        @Test
        fun `ORDER_PAID metric type increments orderCount`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.ORDER_PAID)

            // then
            assertThat(counts.getOrderCount()).isEqualTo(1L)
            assertThat(counts.getViews()).isEqualTo(0L)
            assertThat(counts.getLikes()).isEqualTo(0L)
        }

        @DisplayName("여러 번 호출하면 카운트가 누적된다")
        @Test
        fun `multiple calls accumulate counts`() {
            // given
            val counts = MutableCounts()

            // when
            repeat(5) { counts.increment(MetricType.VIEW) }
            repeat(3) { counts.increment(MetricType.LIKE_CREATED) }
            repeat(2) { counts.increment(MetricType.ORDER_PAID) }

            // then
            assertThat(counts.getViews()).isEqualTo(5L)
            assertThat(counts.getLikes()).isEqualTo(3L)
            assertThat(counts.getOrderCount()).isEqualTo(2L)
        }

        @DisplayName("좋아요 생성 후 취소하면 likes가 0이 된다")
        @Test
        fun `like created then canceled results in zero likes`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.LIKE_CREATED)
            counts.increment(MetricType.LIKE_CANCELED)

            // then
            assertThat(counts.getLikes()).isEqualTo(0L)
        }

        @DisplayName("좋아요 취소만 발생하면 likes가 음수가 된다")
        @Test
        fun `only like canceled results in negative likes`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.LIKE_CANCELED)
            counts.increment(MetricType.LIKE_CANCELED)

            // then
            assertThat(counts.getLikes()).isEqualTo(-2L)
        }
    }

    @DisplayName("addOrderAmount 메서드 테스트")
    @Nested
    inner class AddOrderAmount {

        @DisplayName("주문 금액을 누적할 수 있다")
        @Test
        fun `accumulates order amount`() {
            // given
            val counts = MutableCounts()

            // when
            counts.addOrderAmount(BigDecimal("100.00"))

            // then
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("여러 주문 금액이 누적된다")
        @Test
        fun `multiple order amounts are accumulated`() {
            // given
            val counts = MutableCounts()

            // when
            counts.addOrderAmount(BigDecimal("100.00"))
            counts.addOrderAmount(BigDecimal("50.50"))
            counts.addOrderAmount(BigDecimal("25.25"))

            // then
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal("175.75"))
        }

        @DisplayName("소수점 정밀도가 유지된다")
        @Test
        fun `decimal precision is maintained`() {
            // given
            val counts = MutableCounts()

            // when
            counts.addOrderAmount(BigDecimal("100.123456"))
            counts.addOrderAmount(BigDecimal("200.654321"))

            // then
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal("300.777777"))
        }
    }

    @DisplayName("toSnapshot 메서드 테스트")
    @Nested
    inner class ToSnapshot {

        @DisplayName("현재 상태의 스냅샷을 반환한다")
        @Test
        fun `returns snapshot of current state`() {
            // given
            val counts = MutableCounts()
            counts.increment(MetricType.VIEW)
            counts.increment(MetricType.VIEW)
            counts.increment(MetricType.LIKE_CREATED)
            counts.increment(MetricType.ORDER_PAID)
            counts.addOrderAmount(BigDecimal("500.00"))

            // when
            val snapshot = counts.toSnapshot()

            // then
            assertThat(snapshot.views).isEqualTo(2L)
            assertThat(snapshot.likes).isEqualTo(1L)
            assertThat(snapshot.orderCount).isEqualTo(1L)
            assertThat(snapshot.orderAmount).isEqualByComparingTo(BigDecimal("500.00"))
        }

        @DisplayName("스냅샷은 불변이다 - 원본 변경이 스냅샷에 영향을 주지 않는다")
        @Test
        fun `snapshot is immutable - changes to original do not affect snapshot`() {
            // given
            val counts = MutableCounts()
            counts.increment(MetricType.VIEW)
            val snapshot = counts.toSnapshot()

            // when - 스냅샷 생성 후 원본 변경
            counts.increment(MetricType.VIEW)
            counts.increment(MetricType.VIEW)

            // then - 스냅샷은 변경되지 않음
            assertThat(snapshot.views).isEqualTo(1L)
            assertThat(counts.getViews()).isEqualTo(3L)
        }

        @DisplayName("여러 스냅샷을 생성할 수 있다")
        @Test
        fun `can create multiple snapshots`() {
            // given
            val counts = MutableCounts()

            // when
            counts.increment(MetricType.VIEW)
            val snapshot1 = counts.toSnapshot()

            counts.increment(MetricType.VIEW)
            val snapshot2 = counts.toSnapshot()

            // then
            assertThat(snapshot1.views).isEqualTo(1L)
            assertThat(snapshot2.views).isEqualTo(2L)
        }
    }

    @DisplayName("스레드 안전성 테스트")
    @Nested
    inner class ThreadSafety {

        @DisplayName("동시에 여러 스레드에서 VIEW를 증가시켜도 정확한 카운트를 유지한다")
        @Test
        fun `concurrent VIEW increments maintain correct count`() {
            // given
            val counts = MutableCounts()
            val threadCount = 10
            val incrementsPerThread = 1000
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(incrementsPerThread) {
                            counts.increment(MetricType.VIEW)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // then
            assertThat(counts.getViews()).isEqualTo((threadCount * incrementsPerThread).toLong())
        }

        @DisplayName("동시에 여러 스레드에서 좋아요 생성/취소를 해도 정확한 카운트를 유지한다")
        @Test
        fun `concurrent like operations maintain correct count`() {
            // given
            val counts = MutableCounts()
            val threadCount = 10
            val operationsPerThread = 1000
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when - 절반은 LIKE_CREATED, 절반은 LIKE_CANCELED
            repeat(threadCount) { threadIndex ->
                executor.submit {
                    try {
                        repeat(operationsPerThread) {
                            if (threadIndex % 2 == 0) {
                                counts.increment(MetricType.LIKE_CREATED)
                            } else {
                                counts.increment(MetricType.LIKE_CANCELED)
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // then - 5개 스레드가 +1000, 5개 스레드가 -1000 = 0
            assertThat(counts.getLikes()).isEqualTo(0L)
        }

        @DisplayName("동시에 여러 스레드에서 주문 금액을 추가해도 정확한 합계를 유지한다")
        @Test
        fun `concurrent order amount additions maintain correct total`() {
            // given
            val counts = MutableCounts()
            val threadCount = 10
            val additionsPerThread = 1000
            val amountPerAddition = BigDecimal("10.00")
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(additionsPerThread) {
                            counts.addOrderAmount(amountPerAddition)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // then
            val expectedTotal = BigDecimal("10.00")
                .multiply(BigDecimal(threadCount * additionsPerThread))
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(expectedTotal)
        }

        @DisplayName("동시에 여러 메트릭 타입을 처리해도 각각 정확한 카운트를 유지한다")
        @Test
        fun `concurrent mixed operations maintain correct counts`() {
            // given
            val counts = MutableCounts()
            val threadCount = 12
            val operationsPerThread = 1000
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            // when - 4가지 타입을 각각 3개의 스레드에서 처리
            repeat(threadCount) { threadIndex ->
                executor.submit {
                    try {
                        val metricType = when (threadIndex % 4) {
                            0 -> MetricType.VIEW
                            1 -> MetricType.LIKE_CREATED
                            2 -> MetricType.ORDER_PAID
                            else -> MetricType.LIKE_CANCELED
                        }
                        repeat(operationsPerThread) {
                            counts.increment(metricType)
                            if (metricType == MetricType.ORDER_PAID) {
                                counts.addOrderAmount(BigDecimal.ONE)
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
            // VIEW: 3 threads x 1000 = 3000
            assertThat(counts.getViews()).isEqualTo(3000L)
            // LIKE_CREATED: 3 threads x 1000 = 3000
            // LIKE_CANCELED: 3 threads x 1000 = -3000
            // Total likes: 0
            assertThat(counts.getLikes()).isEqualTo(0L)
            // ORDER_PAID: 3 threads x 1000 = 3000
            assertThat(counts.getOrderCount()).isEqualTo(3000L)
            // Order amount: 3 threads x 1000 x 1 = 3000
            assertThat(counts.getOrderAmount()).isEqualByComparingTo(BigDecimal("3000"))
        }

        @DisplayName("스냅샷 생성과 증가가 동시에 발생해도 안전하다")
        @Test
        fun `snapshot creation is safe during concurrent increments`() {
            // given
            val counts = MutableCounts()
            val incrementThreadCount = 5
            val snapshotThreadCount = 5
            val operationsPerThread = 1000
            val latch = CountDownLatch(incrementThreadCount + snapshotThreadCount)
            val executor = Executors.newFixedThreadPool(incrementThreadCount + snapshotThreadCount)
            val snapshots = mutableListOf<CountSnapshot>()

            // when
            repeat(incrementThreadCount) {
                executor.submit {
                    try {
                        repeat(operationsPerThread) {
                            counts.increment(MetricType.VIEW)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            repeat(snapshotThreadCount) {
                executor.submit {
                    try {
                        repeat(operationsPerThread) {
                            synchronized(snapshots) {
                                snapshots.add(counts.toSnapshot())
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
            // 최종 카운트 확인
            assertThat(counts.getViews()).isEqualTo((incrementThreadCount * operationsPerThread).toLong())

            // 모든 스냅샷의 views 값은 0 이상이어야 함
            assertThat(snapshots).allMatch { it.views >= 0 }

            // 스냅샷 views 값은 증가하는 경향을 보여야 함 (완전히 정렬되진 않을 수 있음)
            val maxViews = snapshots.maxOfOrNull { it.views } ?: 0L
            assertThat(maxViews).isGreaterThan(0L)
        }
    }
}
