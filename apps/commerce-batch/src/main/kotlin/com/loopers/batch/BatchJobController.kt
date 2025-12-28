package com.loopers.batch

import com.loopers.batch.productmetrics.ranking.ProductMonthlyRankingJobConfig
import com.loopers.batch.productmetrics.ranking.ProductWeeklyRankingJobConfig
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 배치 Job 실행 API 컨트롤러
 *
 * 배치 작업을 수동으로 실행할 수 있는 REST API 제공
 */
@RestController
@RequestMapping("/api/v1/batch")
class BatchJobController(
    private val jobLauncher: JobLauncher,
    @Qualifier(ProductWeeklyRankingJobConfig.JOB_NAME)
    private val productWeeklyRankingJob: Job,

    @Qualifier(ProductMonthlyRankingJobConfig.JOB_NAME)
    private val productMonthlyRankingJob: Job,
) {

    /**
     * 주간 상품 랭킹 배치 실행
     *
     * 실행 시점(월~일 기준):
     * - 해당 주간이 끝난 뒤(일요일 종료 이후), 집계 데이터가 모두 적재된 다음 실행
     * - 일반적으로 다음 주 월요일 00:00 이후 실행 권장
     *
     * @param weekStart 주 시작일 (yyyy-MM-dd 형식, 필수)
     * @param weekEnd 주 종료일 (yyyy-MM-dd 형식, 필수)
     *
     * 예시:
     * - POST /api/v1/batch/weekly-ranking?weekStart=2025-01-06&weekEnd=2025-01-12
     */
    @PostMapping("/weekly-ranking")
    fun runWeeklyRanking(
        @RequestParam weekStart: String,
        @RequestParam weekEnd: String,
    ): ResponseEntity<Map<String, Any>> {
        // Job 파라미터 생성
        val params = JobParametersBuilder()
            .addString("weekStart", weekStart)
            .addString("weekEnd", weekEnd)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // Job 실행
        val execution = jobLauncher.run(productWeeklyRankingJob, params)

        return ResponseEntity.ok(
            mapOf(
                "jobName" to ProductWeeklyRankingJobConfig.JOB_NAME,
                "jobId" to execution.jobId,
                "status" to execution.status.name,
                "weekStart" to weekStart,
                "weekEnd" to weekEnd,
                "startTime" to execution.startTime,
            ),
        )
    }

    /**
     * 월별 상품 랭킹 배치 실행
     *
     * 실행 시점:
     * - 해당 월이 끝난 뒤, 그 월을 포함하는 마지막 주간 랭킹이 생성된 다음 실행
     * - 일반적으로 다음 달 1일 00:00 이후 실행 권장
     *
     * @param yearMonth 월 (yyyy-MM 형식, 필수)
     *
     * 예시:
     * - POST /api/v1/batch/monthly-ranking?yearMonth=2025-01
     */
    @PostMapping("/monthly-ranking")
    fun runMonthlyRanking(
        @RequestParam yearMonth: String,
    ): ResponseEntity<Map<String, Any>> {
        // Job 파라미터 생성
        val params = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // Job 실행
        val execution = jobLauncher.run(productMonthlyRankingJob, params)

        return ResponseEntity.ok(
            mapOf(
                "jobName" to ProductMonthlyRankingJobConfig.JOB_NAME,
                "jobId" to execution.jobId,
                "status" to execution.status.name,
                "yearMonth" to yearMonth,
                "startTime" to execution.startTime,
            ),
        )
    }
}
