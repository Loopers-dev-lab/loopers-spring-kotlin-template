package com.loopers.interfaces.api

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.interfaces.api.dto.BatchExecutionRequest
import com.loopers.interfaces.api.dto.BatchExecutionResponse
import com.loopers.support.error.BatchErrorType
import com.loopers.support.error.BatchException
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Admin controller for manual batch execution.
 * Based on api-design-weekly-monthly-ranking.md#2.2
 *
 * Endpoints:
 * - POST /api/v1/admin/batch/rankings/{period}
 */
@RestController
@RequestMapping("/api/v1/admin/batch/rankings")
class BatchRankingController(
    private val jobRepository: JobRepository,
    private val jobOperator: JobOperator,
    private val jobs: Map<String, Job>,
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val SUPPORTED_PERIODS = setOf("weekly", "monthly")
    }

    /**
     * Creates a synchronous JobLauncher for manual execution.
     * Administrators need to immediately verify batch execution results.
     */
    private fun createSyncJobLauncher(): JobLauncher {
        val launcher = TaskExecutorJobLauncher()
        launcher.setJobRepository(jobRepository)
        launcher.setTaskExecutor(SyncTaskExecutor())
        launcher.afterPropertiesSet()
        return launcher
    }

    @PostMapping("/{period}")
    fun executeRankingBatch(
        @PathVariable period: String,
        @RequestBody(required = false) request: BatchExecutionRequest?,
    ): ApiResponse<BatchExecutionResponse> {
        // Validate period
        if (period !in SUPPORTED_PERIODS) {
            throw BatchException(
                BatchErrorType.INVALID_PERIOD,
                "Invalid period type: $period",
            )
        }

        // Parse and validate baseDate
        val baseDateString = request?.baseDate
        val baseDate = parseBaseDate(baseDateString)
        val baseDateFormatted = baseDate.format(DATE_FORMATTER)

        // Get the job by period
        val jobName = getJobName(period)
        val job = jobs[jobName]
            ?: throw BatchException(BatchErrorType.INTERNAL_ERROR, "Job not found: $jobName")

        // Check if job is already running
        if (isJobRunning(jobName)) {
            throw BatchException(BatchErrorType.JOB_ALREADY_RUNNING)
        }

        // Build job parameters
        val jobParameters = JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("timestamp", System.currentTimeMillis()) // For re-execution support
            .toJobParameters()

        // Execute synchronously
        val syncLauncher = createSyncJobLauncher()
        val jobExecution = syncLauncher.run(job, jobParameters)

        val response = BatchExecutionResponse.from(jobExecution, baseDateFormatted)
        return ApiResponse.success(response)
    }

    private fun parseBaseDate(baseDateString: String?): LocalDate {
        if (baseDateString.isNullOrBlank()) {
            return LocalDate.now()
        }

        return try {
            val parsed = LocalDate.parse(baseDateString, DATE_FORMATTER)
            // Future date handling: replace with today
            if (parsed.isAfter(LocalDate.now())) {
                LocalDate.now()
            } else {
                parsed
            }
        } catch (e: DateTimeParseException) {
            throw BatchException(BatchErrorType.INVALID_DATE_FORMAT)
        }
    }

    private fun getJobName(period: String): String {
        return when (period) {
            "weekly" -> WeeklyRankingJobConfig.JOB_NAME
            "monthly" -> MonthlyRankingJobConfig.JOB_NAME
            else -> throw BatchException(BatchErrorType.INVALID_PERIOD, "Invalid period type: $period")
        }
    }

    private fun isJobRunning(jobName: String): Boolean {
        return try {
            val runningExecutions = jobOperator.getRunningExecutions(jobName)
            runningExecutions.isNotEmpty()
        } catch (e: Exception) {
            // NoSuchJobException or other - job is not running
            false
        }
    }
}
