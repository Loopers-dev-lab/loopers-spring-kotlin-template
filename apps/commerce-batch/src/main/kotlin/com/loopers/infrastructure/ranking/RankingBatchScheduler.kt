package com.loopers.infrastructure.ranking

import com.loopers.batch.ranking.MonthlyRankingAggregationJobConfig
import com.loopers.batch.ranking.WeeklyRankingAggregationJobConfig
import org.slf4j.LoggerFactory
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * 랭킹 배치 Job 실행 스케줄러
 */
@Component
class RankingBatchScheduler(private val jobOperator: JobOperator, private val jobRepository: JobRepository) {
    private val logger = LoggerFactory.getLogger(RankingBatchScheduler::class.java)

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
            val targetDate = LocalDate.now().minusDays(1)

            val jobParameters = Properties().apply {
                setProperty("targetDate", targetDate.toString())
                setProperty("timestamp", System.currentTimeMillis().toString())
            }

            logger.info("주간 랭킹 집계 배치 시작: targetDate=$targetDate")

            val executionId = jobOperator.start(WeeklyRankingAggregationJobConfig.JOB_NAME, jobParameters)
            val execution = jobRepository.getJobExecution(executionId)

            logger.info(
                "주간 랭킹 집계 배치 완료: " +
                        "status=${execution?.status}, exitCode=${execution?.exitStatus?.exitCode}",
            )
        } catch (e: Exception) {
            logger.error("주간 랭킹 집계 배치 실행 실패", e)
            // TODO: 운영 환경에서 알림 발송
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
            val targetYearMonth = YearMonth.now().minusMonths(1)
            val targetYearMonthStr = targetYearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"))

            val jobParameters = Properties().apply {
                setProperty("targetYearMonth", targetYearMonthStr)
                setProperty("timestamp", System.currentTimeMillis().toString())
            }

            logger.info("월간 랭킹 집계 배치 시작: targetYearMonth=$targetYearMonthStr")

            val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
            val execution = jobRepository.getJobExecution(executionId)

            logger.info(
                "월간 랭킹 집계 배치 완료: " +
                        "status=${execution?.status}, exitCode=${execution?.exitStatus?.exitCode}",
            )
        } catch (e: Exception) {
            logger.error("월간 랭킹 집계 배치 실행 실패", e)
            // TODO: 운영 환경에서 알림 발송
        }
    }
}
