package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.infrastructure.notification.BatchAlarmNotifier
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
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.launch.JobOperator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * RankingBatchScheduler 단위 테스트
 *
 * 스케줄러가 올바른 Job 파라미터로 배치를 실행하는지 테스트합니다.
 */
@DisplayName("RankingBatchScheduler 단위 테스트")
class RankingBatchSchedulerTest {

    private lateinit var jobOperator: JobOperator
    private lateinit var weeklyRankingAggregationJob: Job
    private lateinit var monthlyRankingAggregationJob: Job
    private lateinit var batchAlarmNotifier: BatchAlarmNotifier
    private lateinit var rankingBatchScheduler: RankingBatchScheduler

    @BeforeEach
    fun setUp() {
        jobOperator = mock()
        weeklyRankingAggregationJob = mock()
        monthlyRankingAggregationJob = mock()
        batchAlarmNotifier = mock()
        rankingBatchScheduler = RankingBatchScheduler(
            jobOperator,
            weeklyRankingAggregationJob,
            monthlyRankingAggregationJob,
            batchAlarmNotifier,
        )
    }

    @Test
    fun `주간 랭킹 집계 배치를 올바른 파라미터로 실행한다`() {
        // given
        val mockExecution: JobExecution = mock()
        whenever(jobOperator.start(eq(weeklyRankingAggregationJob), any())).thenReturn(mockExecution)

        // when
        rankingBatchScheduler.runWeeklyRankingAggregation()

        // then
        val paramsCaptor = ArgumentCaptor.forClass(JobParameters::class.java)
        verify(jobOperator).start(eq(weeklyRankingAggregationJob), paramsCaptor.capture())

        val capturedParams = paramsCaptor.value
        assertThat(capturedParams).isNotNull
        assertThat(capturedParams.getLong("timestamp")).isNotNull

        // targetDate는 어제 (일요일 01:00 실행 시 토요일)
        val expectedTargetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString()
        assertThat(capturedParams.getString("targetDate")).isEqualTo(expectedTargetDate)
    }

    @Test
    fun `월간 랭킹 집계 배치를 올바른 파라미터로 실행한다`() {
        // given
        val mockExecution: JobExecution = mock()
        whenever(jobOperator.start(eq(monthlyRankingAggregationJob), any())).thenReturn(mockExecution)

        // when
        rankingBatchScheduler.runMonthlyRankingAggregation()

        // then
        val paramsCaptor = ArgumentCaptor.forClass(JobParameters::class.java)
        verify(jobOperator).start(eq(monthlyRankingAggregationJob), paramsCaptor.capture())

        val capturedParams = paramsCaptor.value
        assertThat(capturedParams).isNotNull
        assertThat(capturedParams.getLong("timestamp")).isNotNull

        // targetYearMonth는 지난 달
        val expectedYearMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1)
        val expectedYearMonthStr = ProductRankMonthly.yearMonthToString(expectedYearMonth)
        assertThat(capturedParams.getString("targetYearMonth")).isEqualTo(expectedYearMonthStr)
    }

    @Test
    fun `주간 랭킹 배치 실행 실패 시 예외를 로깅하고 알림을 발송한다`() {
        // given
        val exception = RuntimeException("배치 실행 실패")
        whenever(jobOperator.start(eq(weeklyRankingAggregationJob), any()))
            .thenThrow(exception)

        // when: 예외가 발생해도 스케줄러는 예외를 삼키고 계속 실행됨
        rankingBatchScheduler.runWeeklyRankingAggregation() // 예외를 던지지 않아야 함

        // then: 알림이 발송되어야 함
        verify(jobOperator).start(eq(weeklyRankingAggregationJob), any())
        verify(batchAlarmNotifier).notifyBatchFailure(
            eq("주간 랭킹 집계 배치"),
            eq("주간 랭킹 집계 배치 실행 중 오류가 발생했습니다."),
            eq(exception),
        )
    }

    @Test
    fun `월간 랭킹 배치 실행 실패 시 예외를 로깅하고 알림을 발송한다`() {
        // given
        val exception = RuntimeException("배치 실행 실패")
        whenever(jobOperator.start(eq(monthlyRankingAggregationJob), any()))
            .thenThrow(exception)

        // when: 예외가 발생해도 스케줄러는 예외를 삼키고 계속 실행됨
        rankingBatchScheduler.runMonthlyRankingAggregation() // 예외를 던지지 않아야 함

        // then: 알림이 발송되어야 함
        verify(jobOperator).start(eq(monthlyRankingAggregationJob), any())
        verify(batchAlarmNotifier).notifyBatchFailure(
            eq("월간 랭킹 집계 배치"),
            eq("월간 랭킹 집계 배치 실행 중 오류가 발생했습니다."),
            eq(exception),
        )
    }
}
