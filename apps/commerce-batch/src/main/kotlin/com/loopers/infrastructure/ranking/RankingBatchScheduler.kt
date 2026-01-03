package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.infrastructure.notification.BatchAlarmNotifier
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 랭킹 배치 Job 실행 스케줄러
 */
@Component
class RankingBatchScheduler(
    private val jobOperator: JobOperator,
    @param:Qualifier("weeklyRankingAggregationJob") private val weeklyRankingAggregationJob: Job,
    @param:Qualifier("monthlyRankingAggregationJob") private val monthlyRankingAggregationJob: Job,
    private val batchAlarmNotifier: BatchAlarmNotifier,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(RankingBatchScheduler::class.java)
        private val SCHEDULER_ZONE = ZoneId.of("Asia/Seoul")
        private const val WEEKLY_JOB_NAME = "주간 랭킹 집계 배치"
        private const val MONTHLY_JOB_NAME = "월간 랭킹 집계 배치"
    }

    /**
     * 주간 랭킹 집계 배치 실행
     *
     * 매주 일요일 01:00에 실행:
     * - 지난 주(월~일)의 일간 랭킹 데이터를 집계하여 주간 랭킹 생성
     *
     * Cron: "0 0 1 * * SUN" = 매주 일요일 01시 00분 (Asia/Seoul)
     */
    @Scheduled(cron = "0 0 1 * * SUN", zone = "Asia/Seoul")
    fun runWeeklyRankingAggregation() {
        try {
            // 어제(토요일)를 기준으로 지난 주 집계
            val targetDate = LocalDate.now(SCHEDULER_ZONE).minusDays(1)

            val jobParameters = JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            logger.info("주간 랭킹 집계 배치 시작: targetDate=$targetDate")

            val jobExecution = jobOperator.start(weeklyRankingAggregationJob, jobParameters)

            logger.info("주간 랭킹 집계 배치 시작됨: executionId=${jobExecution.id}")
        } catch (e: Exception) {
            logger.error("주간 랭킹 집계 배치 실행 실패", e)
            batchAlarmNotifier.notifyBatchFailure(
                jobName = WEEKLY_JOB_NAME,
                message = "주간 랭킹 집계 배치 실행 중 오류가 발생했습니다.",
                error = e,
            )
        }
    }

    /**
     * 월간 랭킹 집계 배치 실행
     *
     * 매월 1일 02:00에 실행:
     * - 지난 달의 일간 랭킹 데이터를 집계하여 월간 랭킹 생성
     *
     * Cron: "0 0 2 1 * *" = 매월 1일 02시 00분 (Asia/Seoul)
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    fun runMonthlyRankingAggregation() {
        try {
            // 지난 달 집계
            val targetYearMonth = YearMonth.now(SCHEDULER_ZONE).minusMonths(1)
            val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)

            val jobParameters = JobParametersBuilder()
                .addString("targetYearMonth", targetYearMonthStr)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            logger.info("월간 랭킹 집계 배치 시작: targetYearMonth=$targetYearMonthStr")

            val jobExecution = jobOperator.start(monthlyRankingAggregationJob, jobParameters)

            logger.info("월간 랭킹 집계 배치 시작됨: executionId=${jobExecution.id}")
        } catch (e: Exception) {
            logger.error("월간 랭킹 집계 배치 실행 실패", e)
            batchAlarmNotifier.notifyBatchFailure(
                jobName = MONTHLY_JOB_NAME,
                message = "월간 랭킹 집계 배치 실행 중 오류가 발생했습니다.",
                error = e,
            )
        }
    }
}
