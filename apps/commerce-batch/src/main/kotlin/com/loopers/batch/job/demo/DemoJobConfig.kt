package com.loopers.batch.job.demo

import com.loopers.batch.job.demo.step.DemoTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DemoJobConfig.JOB_NAME)
@Configuration
class DemoJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val demoTasklet: DemoTasklet,
) {
    companion object {
        const val JOB_NAME = "demoJob"
        private const val STEP_DEMO_SIMPLE_TASK_NAME = "demoSimpleTask"
    }

    @Bean(JOB_NAME)
    fun demoJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(categorySyncStep())
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_DEMO_SIMPLE_TASK_NAME)
    fun categorySyncStep(): Step {
        return StepBuilder(STEP_DEMO_SIMPLE_TASK_NAME, jobRepository)
            .tasklet(demoTasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
    }
}
