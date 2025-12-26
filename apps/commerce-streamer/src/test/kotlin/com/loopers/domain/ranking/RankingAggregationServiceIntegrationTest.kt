package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.infrastructure.ranking.RankingWeightJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@DisplayName("RankingAggregationService 통합 테스트")
class RankingAggregationServiceIntegrationTest @Autowired constructor(
    private val rankingAggregationService: RankingAggregationService,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
    private val rankingWeightJpaRepository: RankingWeightJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    @BeforeEach
    fun setUp() {
        // 가중치 데이터 설정
        val weight = RankingWeight(
            viewWeight = BigDecimal("0.10"),
            likeWeight = BigDecimal("0.20"),
            orderWeight = BigDecimal("0.60"),
        )
        rankingWeightJpaRepository.save(weight)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("전체 흐름 테스트")
    @Nested
    inner class FullFlow {

        @DisplayName("메트릭 추가 후 flush하면 DB와 Redis에 모두 저장된다")
        @Test
        fun `accumulateMetric and flush persists to both DB and Redis`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetric(command)
            rankingAggregationService.flush()

            // then - DB 검증
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(productId)
            assertThat(metrics[0].viewCount).isEqualTo(1L)

            // then - Redis 검증
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            val score = zSetOps.score(bucketKey, productId.toString())
            assertThat(score).isEqualTo(0.10) // 1 view * 0.10 viewWeight
        }

        @DisplayName("여러 메트릭을 누적한 후 flush하면 합계가 저장된다")
        @Test
        fun `multiple metrics are accumulated before flush`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            // 3 views, 2 likes, 1 order with 1000 amount
            repeat(3) {
                rankingAggregationService.accumulateMetric(
                    AccumulateMetricCommand(
                        items = listOf(
                            AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                        ),
                    ),
                )
            }
            repeat(2) {
                rankingAggregationService.accumulateMetric(
                    AccumulateMetricCommand(
                        items = listOf(
                            AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.LIKE_CREATED, orderAmount = null, occurredAt = occurredAt),
                        ),
                    ),
                )
            }
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.ORDER_PAID, orderAmount = BigDecimal("1000.00"), occurredAt = occurredAt),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then - DB 검증
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].viewCount).isEqualTo(3L)
            assertThat(metrics[0].likeCount).isEqualTo(2L)
            assertThat(metrics[0].orderCount).isEqualTo(1L)
            assertThat(metrics[0].orderAmount).isEqualByComparingTo(BigDecimal("1000.00"))

            // then - Redis 검증
            // Score = 3 * 0.10 + 2 * 0.20 + 1000 * 0.60 = 0.3 + 0.4 + 600 = 600.70
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            val score = zSetOps.score(bucketKey, productId.toString())
            assertThat(score).isEqualTo(600.70)
        }

        @DisplayName("여러 상품의 메트릭이 각각 저장된다")
        @Test
        fun `metrics for multiple products are stored separately`() {
            // given
            val occurredAt = Instant.now()
            val product1Items = listOf(
                AccumulateMetricCommand.Item(productId = 1L, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                AccumulateMetricCommand.Item(productId = 1L, metricType = MetricType.LIKE_CREATED, orderAmount = null, occurredAt = occurredAt),
            )
            val product2Items = listOf(
                AccumulateMetricCommand.Item(productId = 2L, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                AccumulateMetricCommand.Item(productId = 2L, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
            )

            // when
            rankingAggregationService.accumulateMetric(AccumulateMetricCommand(items = product1Items))
            rankingAggregationService.accumulateMetric(AccumulateMetricCommand(items = product2Items))
            rankingAggregationService.flush()

            // then - DB 검증
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(2)

            val product1Metric = metrics.find { it.productId == 1L }
            val product2Metric = metrics.find { it.productId == 2L }

            assertThat(product1Metric!!.viewCount).isEqualTo(1L)
            assertThat(product1Metric.likeCount).isEqualTo(1L)
            assertThat(product2Metric!!.viewCount).isEqualTo(2L)

            // then - Redis 검증
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            // Product 1: 1 * 0.10 + 1 * 0.20 = 0.30
            assertThat(zSetOps.score(bucketKey, "1")).isEqualTo(0.30)
            // Product 2: 2 * 0.10 = 0.20
            assertThat(zSetOps.score(bucketKey, "2")).isEqualTo(0.20)
        }

        @DisplayName("flush 후 새 메트릭은 새 버퍼에 저장된다")
        @Test
        fun `metrics after flush go to new buffer`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            // when - 첫 번째 메트릭 추가 및 flush
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )
            rankingAggregationService.flush()

            // then - DB에 첫 번째 결과 저장됨
            var metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].viewCount).isEqualTo(1L)

            // when - 두 번째 메트릭 추가 및 flush
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )
            rankingAggregationService.flush()

            // then - DB에 누적됨 (upsert)
            metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].viewCount).isEqualTo(2L) // 1 + 1

            // then - Redis 점수도 누적됨 (ZINCRBY)
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            assertThat(zSetOps.score(bucketKey, productId.toString())).isEqualTo(0.20) // 0.10 + 0.10
        }
    }

    @DisplayName("시간 버킷 처리 테스트")
    @Nested
    inner class HourBucketHandling {

        @DisplayName("다른 시간 버킷의 메트릭은 개별 레코드로 저장된다")
        @Test
        fun `metrics in different hour buckets are stored separately`() {
            // given
            val productId = 1L
            val hour14 = Instant.parse("2025-01-15T14:30:00Z")
            val hour15 = Instant.parse("2025-01-15T15:30:00Z")

            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = hour14),
                    ),
                ),
            )
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = hour15),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then - DB에 두 개의 레코드
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(2)
            assertThat(metrics.all { it.productId == productId }).isTrue()

            // then - Redis에도 두 개의 버킷
            val bucket14Key = RankingKeyGenerator.bucketKey(hour14.truncatedTo(ChronoUnit.HOURS))
            val bucket15Key = RankingKeyGenerator.bucketKey(hour15.truncatedTo(ChronoUnit.HOURS))

            assertThat(zSetOps.score(bucket14Key, productId.toString())).isEqualTo(0.10)
            assertThat(zSetOps.score(bucket15Key, productId.toString())).isEqualTo(0.10)
        }

        @DisplayName("시간 경계에서 14:59:58과 15:00:02 메트릭이 각각의 버킷에 저장된다")
        @Test
        fun `metrics at hour boundary are correctly bucketed`() {
            // given
            val productId = 1L
            val item1459 = AccumulateMetricCommand.Item(
                productId = productId,
                metricType = MetricType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T14:59:58Z"),
            )
            val item1500 = AccumulateMetricCommand.Item(
                productId = productId,
                metricType = MetricType.VIEW,
                orderAmount = null,
                occurredAt = Instant.parse("2025-01-15T15:00:02Z"),
            )

            // when
            rankingAggregationService.accumulateMetric(AccumulateMetricCommand(items = listOf(item1459)))
            rankingAggregationService.accumulateMetric(AccumulateMetricCommand(items = listOf(item1500)))
            rankingAggregationService.flush()

            // then - DB 검증
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(2)

            val hour14Metric = metrics.find { it.statHour.toInstant() == Instant.parse("2025-01-15T14:00:00Z") }
            val hour15Metric = metrics.find { it.statHour.toInstant() == Instant.parse("2025-01-15T15:00:00Z") }

            assertThat(hour14Metric).isNotNull
            assertThat(hour15Metric).isNotNull
            assertThat(hour14Metric!!.viewCount).isEqualTo(1L)
            assertThat(hour15Metric!!.viewCount).isEqualTo(1L)
        }
    }

    @DisplayName("좋아요 취소 처리 테스트")
    @Nested
    inner class LikeCancelHandling {

        @DisplayName("좋아요 생성 후 취소하면 likeCount가 0이 된다")
        @Test
        fun `like created then canceled results in zero likeCount`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.LIKE_CREATED, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.LIKE_CANCELED, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].likeCount).isEqualTo(0L)
        }

        @DisplayName("취소만 있으면 likeCount가 음수가 된다")
        @Test
        fun `only cancels result in negative likeCount`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.LIKE_CANCELED, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )
            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.LIKE_CANCELED, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].likeCount).isEqualTo(-2L)
        }
    }

    @DisplayName("Redis TTL 테스트")
    @Nested
    inner class RedisTtl {

        @DisplayName("flush 후 Redis 키에 TTL이 설정된다")
        @Test
        fun `TTL is set on Redis key after flush`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()

            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            val ttl = redisTemplate.getExpire(bucketKey, java.util.concurrent.TimeUnit.SECONDS)

            // TTL이 25시간(90000초) 근처인지 확인
            assertThat(ttl).isGreaterThan(89000L)
            assertThat(ttl).isLessThanOrEqualTo(90000L)
        }
    }

    @DisplayName("가중치 fallback 테스트")
    @Nested
    inner class WeightFallback {

        @DisplayName("가중치가 없으면 fallback 값을 사용한다")
        @Test
        fun `uses fallback weight when no weight configured`() {
            // given - 기존 가중치 삭제
            rankingWeightJpaRepository.deleteAll()

            val productId = 1L
            val occurredAt = Instant.now()

            rankingAggregationService.accumulateMetric(
                AccumulateMetricCommand(
                    items = listOf(
                        AccumulateMetricCommand.Item(productId = productId, metricType = MetricType.VIEW, orderAmount = null, occurredAt = occurredAt),
                    ),
                ),
            )

            // when
            rankingAggregationService.flush()

            // then - fallback weight (view=0.10)이 사용됨
            val bucketKey = RankingKeyGenerator.bucketKey(occurredAt)
            assertThat(zSetOps.score(bucketKey, productId.toString())).isEqualTo(0.10)
        }
    }

    @DisplayName("빈 버퍼 flush 테스트")
    @Nested
    inner class EmptyBufferFlush {

        @DisplayName("버퍼가 비어있으면 flush해도 DB/Redis에 아무것도 저장되지 않는다")
        @Test
        fun `empty buffer flush does not persist anything`() {
            // when
            rankingAggregationService.flush()

            // then
            assertThat(productHourlyMetricJpaRepository.findAll()).isEmpty()
        }
    }

    @DisplayName("버킷 전환 테스트")
    @Nested
    inner class BucketTransition {

        @DisplayName("이전 버킷의 점수에 0.1 감쇠를 적용하여 새 버킷을 생성한다")
        @Test
        fun `transition creates new bucket with decayed scores`() {
            // given - 이전 시간 버킷에 점수 설정
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            // 이전 버킷에 점수 직접 설정
            zSetOps.add(previousBucketKey, "1", 100.0)
            zSetOps.add(previousBucketKey, "2", 50.0)
            zSetOps.add(previousBucketKey, "3", 25.0)

            // when
            rankingAggregationService.transitionBucket()

            // then - 새 버킷에 감쇠된 점수가 있어야 함
            val score1 = zSetOps.score(currentBucketKey, "1")
            val score2 = zSetOps.score(currentBucketKey, "2")
            val score3 = zSetOps.score(currentBucketKey, "3")

            // 100 * 0.1 = 10.0, 50 * 0.1 = 5.0, 25 * 0.1 = 2.5
            assertThat(score1).isEqualTo(10.0)
            assertThat(score2).isEqualTo(5.0)
            assertThat(score3).isEqualTo(2.5)
        }

        @DisplayName("이전 버킷이 없으면 아무 작업도 하지 않는다")
        @Test
        fun `transition does nothing when previous bucket does not exist`() {
            // given - 이전 버킷이 없는 상태
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            // when
            rankingAggregationService.transitionBucket()

            // then - 새 버킷이 생성되지 않음
            assertThat(redisTemplate.hasKey(currentBucketKey)).isFalse()
        }

        @DisplayName("현재 버킷이 이미 존재하면 중복 전환하지 않는다")
        @Test
        fun `transition does not overwrite existing current bucket`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            // 이전 버킷 설정
            zSetOps.add(previousBucketKey, "1", 100.0)

            // 현재 버킷에 기존 점수 설정
            zSetOps.add(currentBucketKey, "1", 999.0)

            // when
            rankingAggregationService.transitionBucket()

            // then - 기존 점수가 유지됨 (덮어쓰지 않음)
            val score = zSetOps.score(currentBucketKey, "1")
            assertThat(score).isEqualTo(999.0)
        }

        @DisplayName("새 버킷에 TTL이 설정된다")
        @Test
        fun `transition sets TTL on new bucket`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            zSetOps.add(previousBucketKey, "1", 100.0)

            // when
            rankingAggregationService.transitionBucket()

            // then - TTL이 25시간(90000초) 근처인지 확인
            val ttl = redisTemplate.getExpire(currentBucketKey, java.util.concurrent.TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(89000L)
            assertThat(ttl).isLessThanOrEqualTo(90000L)
        }

        @DisplayName("전체 전환 흐름: 이벤트 추가 -> flush -> 전환 -> 새 버킷에 감쇠 점수")
        @Test
        fun `full transition flow with events`() {
            // given - 이전 시간대에 이벤트 발생 시뮬레이션
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            // 이전 버킷에 점수 설정 (실제 이벤트 처리 후의 상태)
            // Product 1: 3 views, 2 likes, 1 order with 1000 amount
            // Score = 3 * 0.10 + 2 * 0.20 + 1000 * 0.60 = 0.3 + 0.4 + 600 = 600.70
            zSetOps.add(previousBucketKey, "1", 600.70)

            // when - 버킷 전환
            rankingAggregationService.transitionBucket()

            // then - 새 버킷에 감쇠된 점수
            // 600.70 * 0.1 = 60.07
            val decayedScore = zSetOps.score(currentBucketKey, "1")
            assertThat(decayedScore).isEqualTo(60.07)
        }
    }
}
