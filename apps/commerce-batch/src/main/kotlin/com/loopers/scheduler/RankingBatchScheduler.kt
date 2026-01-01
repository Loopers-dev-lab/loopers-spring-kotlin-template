package com.loopers.scheduler

import com.loopers.support.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingBatchScheduler(
    private val jobLauncher: JobLauncher,
    private val weeklyRankingJob: Job,
    private val monthlyRankingJob: Job,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매주 월요일 새벽 1시 (지난 주 집계)
     */
    @Scheduled(cron = "0 0 1 * * MON")
    fun runWeeklyRanking() {
        val lastWeek = LocalDate.now().minusWeeks(1)
        val yearWeek = DateUtils.toYearWeek(lastWeek)

        log.info("===== 주간 랭킹 스케줄 실행: $yearWeek =====")

        val params = JobParametersBuilder()
            .addString("yearWeek", yearWeek)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        try {
            val execution = jobLauncher.run(weeklyRankingJob, params)
            log.info("주간 랭킹 배치 성공: ${execution.status}")
        } catch (e: Exception) {
            log.error("주간 랭킹 배치 실패: $yearWeek", e)
        }
    }

    /**
     * 매월 1일 새벽 2시 (지난 달 집계)
     */
    @Scheduled(cron = "0 0 2 1 * *")
    fun runMonthlyRanking() {
        val lastMonth = LocalDate.now().minusMonths(1)
        val yearMonth = DateUtils.toYearMonth(lastMonth)

        log.info("===== 월간 랭킹 스케줄 실행: $yearMonth =====")

        val params = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        try {
            val execution = jobLauncher.run(monthlyRankingJob, params)
            log.info("월간 랭킹 배치 성공: ${execution.status}")
        } catch (e: Exception) {
            log.error("월간 랭킹 배치 실패: $yearMonth", e)
        }
    }
}
