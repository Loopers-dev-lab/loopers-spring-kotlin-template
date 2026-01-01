package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
class JobMonitorListener : JobExecutionListener {
    private val log = LoggerFactory.getLogger(JobMonitorListener::class.java)

    override fun beforeJob(jobExecution: JobExecution) {
        log.info("Job '${jobExecution.jobInstance.jobName}' 시작")
    }

    override fun afterJob(jobExecution: JobExecution) {
        val startDateTime = jobExecution.startTime
        val endDateTime = jobExecution.endTime ?: LocalDateTime.now()
        val duration = Duration.between(startDateTime, endDateTime)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        val message = """
            Job '${jobExecution.jobInstance.jobName}' 완료
            *Start Time:* $startDateTime
            *End Time:* $endDateTime
            *Total Time:* ${hours}시간 ${minutes}분 ${seconds}초
            *Status:* ${jobExecution.status}
            *Exit Code:* ${jobExecution.exitStatus.exitCode}
        """.trimIndent()

        log.info(message)
    }
}
