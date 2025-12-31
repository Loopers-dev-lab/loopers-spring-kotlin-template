package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RankingScoreScheduler 단위 테스트")
class RankingScoreSchedulerTest {

    private lateinit var rankingAggregationService: RankingAggregationService
    private lateinit var scheduler: RankingScoreScheduler

    @BeforeEach
    fun setUp() {
        rankingAggregationService = mockk()
        scheduler = RankingScoreScheduler(rankingAggregationService)
    }

    @DisplayName("calculateScores가 RankingAggregationService.calculateAndUpdateScores를 호출한다")
    @Test
    fun `calculateScores calls RankingAggregationService calculateAndUpdateScores`() {
        // given
        every { rankingAggregationService.calculateAndUpdateScores() } just Runs

        // when
        scheduler.calculateScores()

        // then
        verify(exactly = 1) { rankingAggregationService.calculateAndUpdateScores() }
    }
}
