package com.loopers.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
class SchedulerConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.setThreadNamePrefix("batch-scheduler-")
        scheduler.setErrorHandler { throwable ->
            log.error("[SchedulerConfig] Scheduled task failed", throwable)
        }
        scheduler.initialize()
        return scheduler
    }
}
