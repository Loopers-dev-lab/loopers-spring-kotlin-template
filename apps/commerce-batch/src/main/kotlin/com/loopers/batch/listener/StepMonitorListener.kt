package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.stereotype.Component

@Component
class StepMonitorListener : StepExecutionListener {
    private val log = LoggerFactory.getLogger(StepMonitorListener::class.java)

    override fun beforeStep(stepExecution: StepExecution) {
        log.info("Step '${stepExecution.stepName}' 시작")
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        if (stepExecution.failureExceptions.isNotEmpty()) {
            log.info(
                """
                    [에러 발생]
                    jobName: ${stepExecution.jobExecution.jobInstance.jobName}
                    exceptions: 
                    ${stepExecution.failureExceptions.mapNotNull { it.message }.joinToString("\n")}
                """.trimIndent(),
            )
            // error 발생 시 slack 등 다른 채널로 모니터 전송
            return ExitStatus.FAILED
        }
        return ExitStatus.COMPLETED
    }
}
