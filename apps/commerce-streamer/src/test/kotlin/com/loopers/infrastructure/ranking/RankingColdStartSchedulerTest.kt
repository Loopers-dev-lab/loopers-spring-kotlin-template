package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RankingColdStartSchedulerTest {

    private lateinit var rankingService: RankingService
    private lateinit var scheduler: RankingColdStartScheduler

    @BeforeEach
    fun setUp() {
        rankingService = mockk()
        scheduler = RankingColdStartScheduler(rankingService)
    }

    @DisplayName("콜드 스타트 방지 스케줄러가 실행되면 오늘 랭킹 데이터를 10% 가중치로 내일 키로 복사한다")
    @Test
    fun carryOverScoresWithCorrectWeight() {
        // given
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        every { rankingService.carryOverScores(today, tomorrow, 0.1) } returns 3

        // when
        scheduler.executeNow()

        // then
        verify(exactly = 1) {
            rankingService.carryOverScores(
                sourceDate = today,
                targetDate = tomorrow,
                weight = 0.1
            )
        }
    }

    @DisplayName("오늘 랭킹 데이터가 없어도 스케줄러는 정상적으로 실행된다")
    @Test
    fun handleEmptyRankingGracefully() {
        // given
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        every { rankingService.carryOverScores(today, tomorrow, 0.1) } returns 0

        // when
        scheduler.executeNow()

        // then
        verify(exactly = 1) {
            rankingService.carryOverScores(today, tomorrow, 0.1)
        }
    }

    @DisplayName("스케줄러 실행 중 예외가 발생해도 애플리케이션이 중단되지 않는다")
    @Test
    fun handleExceptionGracefully() {
        // given
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        every { rankingService.carryOverScores(today, tomorrow, 0.1) } throws RuntimeException("Redis error")

        // when
        scheduler.executeNow()

        // then
        verify(exactly = 1) {
            rankingService.carryOverScores(today, tomorrow, 0.1)
        }
    }
}
