package com.loopers.interfaces.api.dto

import org.springframework.batch.core.JobExecution
import java.time.format.DateTimeFormatter

/**
 * Response DTO for batch execution result.
 * Based on api-design-weekly-monthly-ranking.md#2.2
 */
data class BatchExecutionResponse(
    val jobName: String,
    val baseDate: String,
    val status: String,
    val startTime: String,
    val endTime: String,
    val readCount: Long,
    val writeCount: Long,
    val exitDescription: String,
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun from(jobExecution: JobExecution, baseDate: String): BatchExecutionResponse {
            val stepExecutions = jobExecution.stepExecutions
            val totalReadCount = stepExecutions.sumOf { it.readCount }
            val totalWriteCount = stepExecutions.sumOf { it.writeCount }

            return BatchExecutionResponse(
                jobName = jobExecution.jobInstance.jobName,
                baseDate = baseDate,
                status = jobExecution.status.name,
                startTime = jobExecution.startTime?.format(formatter) ?: "",
                endTime = jobExecution.endTime?.format(formatter) ?: "",
                readCount = totalReadCount,
                writeCount = totalWriteCount,
                exitDescription = jobExecution.exitStatus.exitDescription ?: "",
            )
        }
    }
}
