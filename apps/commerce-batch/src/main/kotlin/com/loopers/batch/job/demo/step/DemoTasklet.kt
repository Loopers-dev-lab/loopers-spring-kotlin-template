package com.loopers.batch.job.demo.step

import com.loopers.batch.job.demo.DemoJobConfig
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DemoJobConfig.JOB_NAME)
@Component
class DemoTasklet(
    @param:Value("#{jobParameters['requestDate']}") private val requestDate: String,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus? {
        println("Demo Tasklet 실행 (실행 일자 : $requestDate)")
        Thread.sleep(1000)
        println("Demo Tasklet 작업 완료")
        return RepeatStatus.FINISHED
    }
}
