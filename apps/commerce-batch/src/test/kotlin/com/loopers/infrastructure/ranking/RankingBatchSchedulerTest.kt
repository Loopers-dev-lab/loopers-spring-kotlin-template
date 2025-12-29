package com.loopers.infrastructure.ranking

import com.loopers.batch.ranking.MonthlyRankingAggregationJobConfig
import com.loopers.batch.ranking.WeeklyRankingAggregationJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.batch.core.launch.JobOperator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * RankingBatchScheduler 단위 테스트
 *
 * 스케줄러가 올바른 Job 파라미터로 배치를 실행하는지 테스트합니다.
 */
@DisplayName("RankingBatchScheduler 단위 테스트")
class RankingBatchSchedulerTest {

    private lateinit var jobOperator: JobOperator
    private lateinit var rankingBatchScheduler: RankingBatchScheduler

    @BeforeEach
    fun setUp() {
        jobOperator = mock()
        rankingBatchScheduler = RankingBatchScheduler(jobOperator)
    }

    @Test
    fun `주간 랭킹 집계 배치를 올바른 파라미터로 실행한다`() {
        // given
        val executionId = 123L
        whenever(jobOperator.start(eq(WeeklyRankingAggregationJobConfig.JOB_NAME), any())).thenReturn(executionId)

        // when
        rankingBatchScheduler.runWeeklyRankingAggregation()

        // then
        val paramsCaptor = ArgumentCaptor.forClass(Properties::class.java)
        verify(jobOperator).start(eq(WeeklyRankingAggregationJobConfig.JOB_NAME), paramsCaptor.capture())

        val capturedParams = paramsCaptor.value
        assertThat(capturedParams).isNotNull
        assertThat(capturedParams.getProperty("timestamp")).isNotNull

        // targetDate는 어제 (일요일 01:00 실행 시 토요일)
        val expectedTargetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString()
        assertThat(capturedParams.getProperty("targetDate")).isEqualTo(expectedTargetDate)
    }

    @Test
    fun `월간 랭킹 집계 배치를 올바른 파라미터로 실행한다`() {
        // given
        val executionId = 456L
        whenever(jobOperator.start(eq(MonthlyRankingAggregationJobConfig.JOB_NAME), any())).thenReturn(executionId)

        // when
        rankingBatchScheduler.runMonthlyRankingAggregation()

        // then
        val paramsCaptor = ArgumentCaptor.forClass(Properties::class.java)
        verify(jobOperator).start(eq(MonthlyRankingAggregationJobConfig.JOB_NAME), paramsCaptor.capture())

        val capturedParams = paramsCaptor.value
        assertThat(capturedParams).isNotNull
        assertThat(capturedParams.getProperty("timestamp")).isNotNull

        // targetYearMonth는 지난 달
        val expectedYearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1)
        val expectedYearMonthStr = expectedYearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"))
        assertThat(capturedParams.getProperty("targetYearMonth")).isEqualTo(expectedYearMonthStr)
    }

    @Test
    fun `주간 랭킹 배치 실행 실패 시 예외를 로깅한다`() {
        // given
        whenever(jobOperator.start(eq(WeeklyRankingAggregationJobConfig.JOB_NAME), any()))
            .thenThrow(RuntimeException("배치 실행 실패"))

        // when/then: 예외가 발생해도 스케줄러는 계속 실행되어야 함 (예외를 로깅만)
        try {
            rankingBatchScheduler.runWeeklyRankingAggregation()
            // 예외를 삼키므로 여기까지 도달
        } catch (e: Exception) {
            // 예외가 throw되지 않아야 함
            assert(false) { "스케줄러는 예외를 로깅만 하고 삼켜야 합니다" }
        }

        verify(jobOperator).start(eq(WeeklyRankingAggregationJobConfig.JOB_NAME), any())
    }

    @Test
    fun `월간 랭킹 배치 실행 실패 시 예외를 로깅한다`() {
        // given
        whenever(jobOperator.start(eq(MonthlyRankingAggregationJobConfig.JOB_NAME), any()))
            .thenThrow(RuntimeException("배치 실행 실패"))

        // when/then: 예외가 발생해도 스케줄러는 계속 실행되어야 함 (예외를 로깅만)
        try {
            rankingBatchScheduler.runMonthlyRankingAggregation()
            // 예외를 삼키므로 여기까지 도달
        } catch (e: Exception) {
            // 예외가 throw되지 않아야 함
            assert(false) { "스케줄러는 예외를 로깅만 하고 삼켜야 합니다" }
        }

        verify(jobOperator).start(eq(MonthlyRankingAggregationJobConfig.JOB_NAME), any())
    }
}
