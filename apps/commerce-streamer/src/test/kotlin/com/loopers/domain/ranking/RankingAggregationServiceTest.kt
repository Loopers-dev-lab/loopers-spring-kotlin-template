package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("RankingAggregationService 단위 테스트")
class RankingAggregationServiceTest {

    private lateinit var metricRepository: ProductHourlyMetricRepository
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var service: RankingAggregationService

    @BeforeEach
    fun setUp() {
        metricRepository = mockk(relaxed = true)
        rankingWriter = mockk(relaxed = true)
        rankingWeightRepository = mockk()
        scoreCalculator = RankingScoreCalculator()
        service = RankingAggregationService(
            metricRepository = metricRepository,
            rankingWriter = rankingWriter,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
        )
    }

    @DisplayName("add 메서드 테스트")
    @Nested
    inner class Add {

        @DisplayName("이벤트를 버퍼에 추가하면 카운트가 누적된다")
        @Test
        fun `adds event to buffer and accumulates count`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val event = RankingEvent(
                productId = productId,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = occurredAt,
            )
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event)
            service.add(event)
            service.add(event)

            // then - flush로 검증
            service.flush()

            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(1)
            assertThat(rows[0].productId).isEqualTo(productId)
            assertThat(rows[0].viewCount).isEqualTo(3L)
        }

        @DisplayName("여러 상품의 이벤트가 각각 누적된다")
        @Test
        fun `accumulates events for multiple products separately`() {
            // given
            val occurredAt = Instant.now()
            val event1 = RankingEvent(productId = 1L, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = occurredAt)
            val event2 = RankingEvent(productId = 2L, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = occurredAt)
            val event3 = RankingEvent(productId = 1L, eventType = RankingEventType.LIKE_CREATED, orderAmount = null, occurredAt = occurredAt)
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event1)
            service.add(event2)
            service.add(event3)
            service.flush()

            // then
            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(2)

            val product1Row = rows.find { it.productId == 1L }
            val product2Row = rows.find { it.productId == 2L }

            assertThat(product1Row!!.viewCount).isEqualTo(1L)
            assertThat(product1Row.likeCount).isEqualTo(1L)
            assertThat(product2Row!!.viewCount).isEqualTo(1L)
        }

        @DisplayName("주문 이벤트의 금액이 누적된다")
        @Test
        fun `accumulates order amount for ORDER_PAID events`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val event1 = RankingEvent(
                productId = productId,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = BigDecimal("1000.00"),
                occurredAt = occurredAt,
            )
            val event2 = RankingEvent(
                productId = productId,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = BigDecimal("500.00"),
                occurredAt = occurredAt,
            )
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event1)
            service.add(event2)
            service.flush()

            // then
            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(1)
            assertThat(rows[0].orderCount).isEqualTo(2L)
            assertThat(rows[0].orderAmount).isEqualByComparingTo(BigDecimal("1500.00"))
        }

        @DisplayName("좋아요 취소 이벤트는 likeCount를 감소시킨다")
        @Test
        fun `LIKE_CANCELED decrements likeCount`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val event = RankingEvent(productId = productId, eventType = RankingEventType.LIKE_CANCELED, orderAmount = null, occurredAt = occurredAt)
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event)
            service.flush()

            // then
            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(1)
            assertThat(rows[0].likeCount).isEqualTo(-1L)
        }

        @DisplayName("이벤트의 occurredAt에 따라 시간 버킷이 결정된다")
        @Test
        fun `hour bucket is determined by event occurredAt`() {
            // given
            val productId = 1L
            val hour14 = Instant.parse("2025-01-15T14:30:00Z")
            val hour15 = Instant.parse("2025-01-15T15:30:00Z")

            val event14 = RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = hour14)
            val event15 = RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = hour15)
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            val allCapturedRows = mutableListOf<ProductHourlyMetricRow>()
            every { metricRepository.batchAccumulateCounts(any()) } answers {
                allCapturedRows.addAll(firstArg<List<ProductHourlyMetricRow>>())
            }

            // when
            service.add(event14)
            service.add(event15)
            service.flush()

            // then - 두 개의 다른 버킷으로 저장됨 (persistBucket is called once per hour bucket)
            assertThat(allCapturedRows).hasSize(2)

            val row14 = allCapturedRows.find { it.statHour == hour14.truncatedTo(ChronoUnit.HOURS) }
            val row15 = allCapturedRows.find { it.statHour == hour15.truncatedTo(ChronoUnit.HOURS) }

            assertThat(row14).isNotNull
            assertThat(row15).isNotNull
            assertThat(row14!!.viewCount).isEqualTo(1L)
            assertThat(row15!!.viewCount).isEqualTo(1L)
        }
    }

    @DisplayName("flush 메서드 테스트")
    @Nested
    inner class Flush {

        @DisplayName("버퍼가 비어있으면 아무 작업도 하지 않는다")
        @Test
        fun `does nothing when buffer is empty`() {
            // when
            service.flush()

            // then
            verify(exactly = 0) { metricRepository.batchAccumulateCounts(any()) }
            verify(exactly = 0) { rankingWriter.incrementScores(any(), any()) }
        }

        @DisplayName("버퍼를 비우고 새 버퍼로 교체한다")
        @Test
        fun `clears buffer and replaces with new one`() {
            // given
            val event = RankingEvent(productId = 1L, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = Instant.now())
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            service.add(event)

            // when
            service.flush()

            // then - 첫 번째 flush에서 1개 저장
            val firstSlot = slot<List<ProductHourlyMetricRow>>()
            verify(exactly = 1) { metricRepository.batchAccumulateCounts(capture(firstSlot)) }
            assertThat(firstSlot.captured).hasSize(1)

            // when - 두 번째 flush는 버퍼가 비어있음
            service.flush()

            // then - 두 번째 호출은 없음
            verify(exactly = 1) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("Redis에 점수를 증분 업데이트한다")
        @Test
        fun `increments scores in Redis`() {
            // given
            val productId = 1L
            val event = RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = Instant.now())
            val weight = RankingWeight.fallback() // viewWeight = 0.10
            every { rankingWeightRepository.findLatest() } returns weight

            // when
            service.add(event)
            service.flush()

            // then
            val bucketKeySlot = slot<String>()
            val deltasSlot = slot<Map<Long, Score>>()
            verify { rankingWriter.incrementScores(capture(bucketKeySlot), capture(deltasSlot)) }

            assertThat(bucketKeySlot.captured).startsWith("ranking:hourly:")
            val deltas = deltasSlot.captured
            assertThat(deltas).hasSize(1)
            assertThat(deltas[productId]!!.value).isEqualByComparingTo(BigDecimal("0.10"))
        }

        @DisplayName("Redis에 TTL을 설정한다")
        @Test
        fun `sets TTL in Redis`() {
            // given
            val event = RankingEvent(productId = 1L, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = Instant.now())
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event)
            service.flush()

            // then
            val ttlSlot = slot<Long>()
            verify { rankingWriter.setTtl(any(), capture(ttlSlot)) }
            assertThat(ttlSlot.captured).isEqualTo(Duration.ofHours(25).seconds)
        }

        @DisplayName("가중치 조회 실패 시 fallback 가중치를 사용한다")
        @Test
        fun `uses fallback weight when weight query fails`() {
            // given
            val productId = 1L
            val event = RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = Instant.now())
            every { rankingWeightRepository.findLatest() } returns null

            // when
            service.add(event)
            service.flush()

            // then - fallback weight (view = 0.10)가 사용됨
            val deltasSlot = slot<Map<Long, Score>>()
            verify { rankingWriter.incrementScores(any(), capture(deltasSlot)) }

            val deltas = deltasSlot.captured
            assertThat(deltas[productId]!!.value).isEqualByComparingTo(BigDecimal("0.10"))
        }

        @DisplayName("점수 공식이 올바르게 계산된다")
        @Test
        fun `score formula is correctly calculated`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            // 2 views, 3 likes, 1 order with 1000 amount
            val viewEvents = (1..2).map { RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = occurredAt) }
            val likeEvents = (1..3).map { RankingEvent(productId = productId, eventType = RankingEventType.LIKE_CREATED, orderAmount = null, occurredAt = occurredAt) }
            val orderEvent = RankingEvent(productId = productId, eventType = RankingEventType.ORDER_PAID, orderAmount = BigDecimal("1000.00"), occurredAt = occurredAt)

            // weight: view=0.10, like=0.20, order=0.60
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            every { rankingWeightRepository.findLatest() } returns weight

            // when
            viewEvents.forEach { service.add(it) }
            likeEvents.forEach { service.add(it) }
            service.add(orderEvent)
            service.flush()

            // then
            // Score = 2 * 0.10 + 3 * 0.20 + 1000 * 0.60 = 0.2 + 0.6 + 600 = 600.80
            val deltasSlot = slot<Map<Long, Score>>()
            verify { rankingWriter.incrementScores(any(), capture(deltasSlot)) }

            val deltas = deltasSlot.captured
            assertThat(deltas[productId]!!.value).isEqualByComparingTo(BigDecimal("600.80"))
        }
    }

    @DisplayName("동시성 테스트")
    @Nested
    inner class Concurrency {

        @DisplayName("flush 중에 add된 이벤트는 새 버퍼에 저장된다")
        @Test
        fun `events added during flush go to new buffer`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // 첫 번째 이벤트 추가
            service.add(RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = occurredAt))

            // when - flush 호출
            service.flush()

            // then - 첫 번째 flush에서 1개 저장
            verify(exactly = 1) { metricRepository.batchAccumulateCounts(any()) }

            // when - flush 후 새 이벤트 추가
            service.add(RankingEvent(productId = productId, eventType = RankingEventType.VIEW, orderAmount = null, occurredAt = occurredAt))
            service.flush()

            // then - 두 번째 flush에서도 1개 저장 (새 버퍼에 있던 것)
            verify(exactly = 2) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("여러 스레드에서 동시에 add해도 모든 이벤트가 누적된다")
        @Test
        fun `concurrent adds accumulate all events`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val threadCount = 10
            val eventsPerThread = 100
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            repeat(threadCount) {
                executor.submit {
                    try {
                        repeat(eventsPerThread) {
                            service.add(
                                RankingEvent(
                                    productId = productId,
                                    eventType = RankingEventType.VIEW,
                                    orderAmount = null,
                                    occurredAt = occurredAt,
                                ),
                            )
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            service.flush()

            // then
            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(1)
            assertThat(rows[0].viewCount).isEqualTo((threadCount * eventsPerThread).toLong())
        }

        @DisplayName("flush와 add가 동시에 발생해도 이벤트가 손실되지 않는다")
        @Test
        fun `no events are lost during concurrent flush and add`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val addThreadCount = 5
            val eventsPerThread = 100
            val flushCount = 10
            val addLatch = CountDownLatch(addThreadCount)
            val flushLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(addThreadCount + 1)
            val totalRowsCaptured = mutableListOf<ProductHourlyMetricRow>()

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } answers {
                val rows = firstArg<List<ProductHourlyMetricRow>>()
                synchronized(totalRowsCaptured) {
                    totalRowsCaptured.addAll(rows)
                }
            }

            // when - add 스레드들
            repeat(addThreadCount) {
                executor.submit {
                    try {
                        repeat(eventsPerThread) {
                            service.add(
                                RankingEvent(
                                    productId = productId,
                                    eventType = RankingEventType.VIEW,
                                    orderAmount = null,
                                    occurredAt = occurredAt,
                                ),
                            )
                            Thread.sleep(1) // add 사이에 약간의 딜레이
                        }
                    } finally {
                        addLatch.countDown()
                    }
                }
            }

            // when - flush 스레드
            executor.submit {
                try {
                    repeat(flushCount) {
                        Thread.sleep(50) // flush 사이에 딜레이
                        service.flush()
                    }
                } finally {
                    flushLatch.countDown()
                }
            }

            addLatch.await(30, TimeUnit.SECONDS)
            flushLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // 마지막 flush로 남은 이벤트 처리
            service.flush()

            // then - 모든 이벤트가 저장됨
            val totalViewCount = totalRowsCaptured.sumOf { it.viewCount }
            assertThat(totalViewCount).isEqualTo((addThreadCount * eventsPerThread).toLong())
        }
    }

    @DisplayName("시간 버킷 경계 처리 테스트")
    @Nested
    inner class HourBoundary {

        @DisplayName("시간 경계에서 두 버킷의 이벤트가 혼합되어도 올바르게 분리된다")
        @Test
        fun `events at hour boundary are correctly separated into buckets`() {
            // given
            val productId = 1L
            // 14:59:58와 15:00:02에 발생한 이벤트
            val event1459 = RankingEvent(
                productId = productId,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T14:59:58Z"),
            )
            val event1500 = RankingEvent(
                productId = productId,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T15:00:02Z"),
            )
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            val allCapturedRows = mutableListOf<ProductHourlyMetricRow>()
            every { metricRepository.batchAccumulateCounts(any()) } answers {
                allCapturedRows.addAll(firstArg<List<ProductHourlyMetricRow>>())
            }

            // when
            service.add(event1459)
            service.add(event1500)
            service.flush()

            // then - persistBucket is called once per hour bucket
            assertThat(allCapturedRows).hasSize(2)

            val hour14Row = allCapturedRows.find { it.statHour == Instant.parse("2025-01-15T14:00:00Z") }
            val hour15Row = allCapturedRows.find { it.statHour == Instant.parse("2025-01-15T15:00:00Z") }

            assertThat(hour14Row).isNotNull
            assertThat(hour15Row).isNotNull
            assertThat(hour14Row!!.viewCount).isEqualTo(1L)
            assertThat(hour15Row!!.viewCount).isEqualTo(1L)
        }

        @DisplayName("flush가 시간 경계를 넘어도 올바른 버킷에 저장된다")
        @Test
        fun `events are stored in correct bucket even if flush crosses hour boundary`() {
            // given
            val productId = 1L
            // 14:59:58에 발생한 이벤트 (15:00:02에 flush되어도 14:00 버킷에 저장)
            val event = RankingEvent(
                productId = productId,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T14:59:58Z"),
            )
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()

            // when
            service.add(event)
            service.flush()

            // then
            val rowsSlot = slot<List<ProductHourlyMetricRow>>()
            verify { metricRepository.batchAccumulateCounts(capture(rowsSlot)) }

            val rows = rowsSlot.captured
            assertThat(rows).hasSize(1)
            assertThat(rows[0].statHour).isEqualTo(Instant.parse("2025-01-15T14:00:00Z"))
        }
    }
}
