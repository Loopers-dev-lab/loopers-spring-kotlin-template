package com.loopers.infrastructure.pg

import feign.Logger
import feign.Request
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PgClientConfig {

    @Bean
    fun feignRequestOptions(): Request.Options {
        return Request.Options(
            1000, // connectTimeout (1초)
            3000,  // readTimeout (3초)
        )
    }

    @Bean
    fun feignLoggerLevel(): Logger.Level {
        return Logger.Level.FULL
    }
}
