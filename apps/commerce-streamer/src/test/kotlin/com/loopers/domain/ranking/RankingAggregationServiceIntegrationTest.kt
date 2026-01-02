package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * RankingAggregationService 통합 테스트
 *
 * Note: calculateHourRankings(), rollupHourlyToDaily(), calculateDailyRankings() 테스트는
 * commerce-batch 모듈의 Spring Batch Job 통합 테스트로 마이그레이션되었습니다:
 * - HourlyRankingJobIntegrationTest
 * - TodayDailyRollupJobIntegrationTest
 * - YesterdayReconciliationJobIntegrationTest
 * - DailyRankingJobIntegrationTest
 */
@SpringBootTest
@DisplayName("RankingAggregationService 통합 테스트")
class RankingAggregationServiceIntegrationTest @Autowired constructor(
    private val rankingAggregationService: RankingAggregationService,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("accumulateMetrics 통합 테스트")
    @Nested
    inner class AccumulateMetricsIntegration {

        @DisplayName("배치 커맨드로 DB에 메트릭을 저장한다")
        @Test
        fun `saves metrics to DB via batch command`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val command = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                        likeCreatedDelta = 5,
                        likeCanceledDelta = 2,
                        orderAmountDelta = BigDecimal("1000.00"),
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(1L)
            assertThat(metrics[0].viewCount).isEqualTo(10L)
            assertThat(metrics[0].likeCount).isEqualTo(3L) // 5 - 2
            assertThat(metrics[0].orderAmount).isEqualByComparingTo(BigDecimal("1000.00"))
        }

        @DisplayName("여러 상품의 메트릭을 한 번에 저장한다")
        @Test
        fun `saves multiple products metrics at once`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val command = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 2L,
                        statHour = statHour,
                        viewDelta = 20,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 3L,
                        statHour = statHour,
                        viewDelta = 30,
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(3)
            assertThat(metrics.map { it.productId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        }

        @DisplayName("동일 상품-시간에 대해 누적된다 (upsert)")
        @Test
        fun `accumulates for same product-hour combination`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val command1 = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                    ),
                ),
            )
            val command2 = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 5,
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command1)
            rankingAggregationService.accumulateMetrics(command2)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].viewCount).isEqualTo(15L) // 10 + 5
        }
    }
}
