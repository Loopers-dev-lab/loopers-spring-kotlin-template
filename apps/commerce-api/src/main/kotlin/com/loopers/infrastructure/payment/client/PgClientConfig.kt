package com.loopers.infrastructure.payment.client

import feign.Logger
import feign.Request
import feign.Retryer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class PgClientConfig {
    @Bean
    fun feignLoggerLevel(): Logger.Level = Logger.Level.FULL

    @Bean
    fun feignOptions(): Request.Options = Request.Options(
            1000L,
            TimeUnit.MILLISECONDS,
            3000L,
            TimeUnit.MILLISECONDS,
            true,
        )

    @Bean
    fun feignRetryer(): Retryer = Retryer.NEVER_RETRY
}
