package com.loopers.interfaces.batch

import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/batch")
class BatchTestController(
    private val jobLauncher: JobLauncher,
    private val weeklyRankingJob: Job,
    private val monthlyRankingJob: Job,
) {

    @PostMapping("/weekly")
    fun runWeekly(@RequestParam yearWeek: String): String {
        val params = JobParametersBuilder()
            .addString("yearWeek", yearWeek)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobLauncher.run(weeklyRankingJob, params)
        return "주간 랭킹 배치: ${execution.status}"
    }

    @PostMapping("/monthly")  // 추가
    fun runMonthly(@RequestParam yearMonth: String): String {
        val params = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobLauncher.run(monthlyRankingJob, params)
        return "월간 랭킹 배치: ${execution.status}"
    }
}
