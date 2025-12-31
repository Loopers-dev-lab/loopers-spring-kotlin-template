package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DailyMetricRollupScheduler 테스트")
class DailyMetricRollupSchedulerTest {

    private lateinit var rankingAggregationService: RankingAggregationService
    private lateinit var scheduler: DailyMetricRollupScheduler

    @BeforeEach
    fun setUp() {
        rankingAggregationService = mockk()
        scheduler = DailyMetricRollupScheduler(rankingAggregationService)
    }

    @DisplayName("rollupAndCalculateDaily는 rollupHourlyToDaily와 calculateDailyRankings를 순서대로 호출한다")
    @Test
    fun `rollupAndCalculateDaily calls rollupHourlyToDaily and calculateDailyRankings in order`() {
        // given
        every { rankingAggregationService.rollupHourlyToDaily() } just Runs
        every { rankingAggregationService.calculateDailyRankings() } just Runs

        // when
        scheduler.rollupAndCalculateDaily()

        // then
        verifyOrder {
            rankingAggregationService.rollupHourlyToDaily()
            rankingAggregationService.calculateDailyRankings()
        }
    }

    @DisplayName("rollupAndCalculateDaily는 서비스 메서드를 각각 한 번씩 호출한다")
    @Test
    fun `rollupAndCalculateDaily calls each service method exactly once`() {
        // given
        every { rankingAggregationService.rollupHourlyToDaily() } just Runs
        every { rankingAggregationService.calculateDailyRankings() } just Runs

        // when
        scheduler.rollupAndCalculateDaily()

        // then
        verify(exactly = 1) { rankingAggregationService.rollupHourlyToDaily() }
        verify(exactly = 1) { rankingAggregationService.calculateDailyRankings() }
    }
}
