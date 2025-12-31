package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.annotation.AfterJob
import org.springframework.batch.core.annotation.BeforeJob
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Component
class JobListener {
    private val log = LoggerFactory.getLogger(JobListener::class.java)

    @BeforeJob
    fun beforeJob(jobExecution: JobExecution) {
        log.info("Job '${jobExecution.jobInstance.jobName}' 시작")
        jobExecution.executionContext.putLong("startTime", System.currentTimeMillis())
    }

    @AfterJob
    fun afterJob(jobExecution: JobExecution) {
        val startTime = jobExecution.executionContext.getLong("startTime")
        val endTime = System.currentTimeMillis()

        val startDateTime = Instant.ofEpochMilli(startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val endDateTime = Instant.ofEpochMilli(endTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val totalTime = endTime - startTime
        val duration = Duration.ofMillis(totalTime)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        val message = """  
            *Start Time:* $startDateTime
            *End Time:* $endDateTime
            *Total Time:* ${hours}시간 ${minutes}분 ${seconds}초
        """.trimIndent()

        log.info(message)
    }
}
