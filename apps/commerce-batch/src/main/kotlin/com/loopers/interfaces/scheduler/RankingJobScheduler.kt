package com.loopers.interfaces.scheduler

import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(
    name = ["scheduler.ranking.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingJobScheduler(
    private val jobLauncher: JobLauncher,
    private val jobRegistry: JobRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    fun runHourlyRankingJob() {
        val jobName = "hourlyRankingJob"
        val now = ZonedDateTime.now(SEOUL_ZONE)
        val params = JobParametersBuilder()
            .addString("baseDateTime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .toJobParameters()

        runJob(jobName, params)
    }

    @Scheduled(cron = "0 0 1,7,13,19 * * *", zone = "Asia/Seoul")
    fun runTodayDailyRollupJob() {
        val jobName = "todayDailyRollupJob"
        val today = LocalDate.now(SEOUL_ZONE)
        val params = JobParametersBuilder()
            .addLocalDate("baseDate", today)
            .toJobParameters()

        runJob(jobName, params)
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun runYesterdayReconciliationJob() {
        val jobName = "yesterdayReconciliationJob"
        val yesterday = LocalDate.now(SEOUL_ZONE).minusDays(1)
        val params = JobParametersBuilder()
            .addLocalDate("baseDate", yesterday)
            .toJobParameters()

        runJob(jobName, params)
    }

    @Scheduled(cron = "0 0 1,13 * * *", zone = "Asia/Seoul")
    fun runDailyRankingJob() {
        val jobName = "dailyRankingJob"
        val today = LocalDate.now(SEOUL_ZONE)
        val params = JobParametersBuilder()
            .addLocalDate("baseDate", today)
            .toJobParameters()

        runJob(jobName, params)
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    fun runWeeklyRankingJob() {
        val jobName = "weeklyRankingJob"
        val today = LocalDate.now(SEOUL_ZONE)
        val params = JobParametersBuilder()
            .addLocalDate("baseDate", today)
            .toJobParameters()

        runJob(jobName, params)
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    fun runMonthlyRankingJob() {
        val jobName = "monthlyRankingJob"
        val today = LocalDate.now(SEOUL_ZONE)
        val params = JobParametersBuilder()
            .addLocalDate("baseDate", today)
            .toJobParameters()

        runJob(jobName, params)
    }

    private fun runJob(jobName: String, params: JobParameters) {
        try {
            val job = jobRegistry.getJob(jobName)
            val execution = jobLauncher.run(job, params)
            val duration = if (execution.startTime != null && execution.endTime != null) {
                Duration.between(execution.startTime, execution.endTime).toMillis()
            } else {
                0L
            }
            log.info(
                "[RankingJobScheduler] {} completed - status={}, duration={}ms",
                jobName,
                execution.status,
                duration,
            )
        } catch (e: JobInstanceAlreadyCompleteException) {
            log.info("[RankingJobScheduler] {} already completed for params={}", jobName, params)
        } catch (e: NoSuchJobException) {
            log.error("[RankingJobScheduler] {} not found in registry", jobName, e)
        } catch (e: Exception) {
            log.error("[RankingJobScheduler] {} failed", jobName, e)
        }
    }
}
