package com.loopers.batch.listener

import com.loopers.infrastructure.notification.BatchAlarmNotifier
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

@Component
class StepMonitorListener(private val batchAlarmNotifier: BatchAlarmNotifier) : StepExecutionListener {
    private val log = LoggerFactory.getLogger(StepMonitorListener::class.java)

    override fun beforeStep(stepExecution: StepExecution) {
        log.info("Step '${stepExecution.stepName}' 시작")
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        val readCount = stepExecution.readCount
        val writeCount = stepExecution.writeCount
        val commitCount = stepExecution.commitCount

        log.info(
            "Step '${stepExecution.stepName}' 완료 - " +
                    "readCount: $readCount, writeCount: $writeCount, commitCount: $commitCount",
        )

        if (stepExecution.failureExceptions.isNotEmpty()) {
            val jobName = stepExecution.jobExecution.jobInstance.jobName
            val stepName = stepExecution.stepName
            val exceptions = stepExecution.failureExceptions.mapNotNull { it.message }.joinToString("\n")

            log.error(
                """
                [배치 Step 에러 발생]
                jobName: $jobName
                stepName: $stepName
                exceptions:
                $exceptions
                """.trimIndent(),
            )

            // BatchAlarmNotifier를 통해 알림 전송
            batchAlarmNotifier.notifyBatchFailure(
                jobName = "$jobName - $stepName",
                message = "Step 실행 중 오류가 발생했습니다.",
                error = stepExecution.failureExceptions.firstOrNull() ?: Exception("Unknown error"),
            )

            return ExitStatus.FAILED
        }

        return ExitStatus.COMPLETED
    }
}
