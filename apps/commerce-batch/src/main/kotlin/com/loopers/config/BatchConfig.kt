package com.loopers.config

import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BatchConfig {

    @Bean
    fun jobRegistryBeanPostProcessor(jobRegistry: JobRegistry): JobRegistryBeanPostProcessor {
        val postProcessor = JobRegistryBeanPostProcessor()
        postProcessor.setJobRegistry(jobRegistry)
        return postProcessor
    }
}
