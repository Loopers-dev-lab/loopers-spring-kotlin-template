package com.loopers.interfaces.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Profile("!prod")
@RestController
@RequestMapping("/batch")
class BatchTestController(
    private val jobLauncher: JobLauncher,
    private val weeklyRankingJob: Job,
    private val monthlyRankingJob: Job,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/weekly")
    fun runWeekly(@RequestParam yearWeek: String): String {
        // 형식 검증: yyyy-Www
        require(yearWeek.matches(Regex("\\d{4}-W\\d{2}"))) {
            "Invalid yearWeek format. Expected: yyyy-Www (e.g., 2025-W52)"
        }

        return try {
            val params = JobParametersBuilder()
                .addString("yearWeek", yearWeek)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            val execution = jobLauncher.run(weeklyRankingJob, params)
            "주간 랭킹 배치: ${execution.status}"
        } catch (e: Exception) {
            log.error("주간 랭킹 배치 실행 실패: yearWeek=$yearWeek", e)
            "주간 랭킹 배치 실행 실패: ${e.message}"
        }
    }

    @PostMapping("/monthly")
    fun runMonthly(@RequestParam yearMonth: String): String {
        // 형식 검증: yyyy-MM
        require(yearMonth.matches(Regex("\\d{4}-\\d{2}"))) {
            "Invalid yearMonth format. Expected: yyyy-MM (e.g., 2025-12)"
        }

        return try {
            val params = JobParametersBuilder()
                .addString("yearMonth", yearMonth)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            val execution = jobLauncher.run(monthlyRankingJob, params)
            "월간 랭킹 배치: ${execution.status}"
        } catch (e: Exception) {
            log.error("월간 랭킹 배치 실행 실패: yearMonth=$yearMonth", e)
            "월간 랭킹 배치 실행 실패: ${e.message}"
        }
    }
}
