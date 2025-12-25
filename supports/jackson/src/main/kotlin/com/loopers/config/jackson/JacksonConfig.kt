package com.loopers.config.jackson

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Jackson configuration for Spring Boot 4.0.
 * Provides JsonMapper bean with Kotlin and Java Time support.
 * Note: JavaTimeModule is built-in to Jackson 3.0, no need to register it.
 * Note: WRITE_DATES_AS_TIMESTAMPS moved from SerializationFeature to DateTimeFeature in Jackson 3.0
 */
@Configuration
class JacksonConfig {

    @Bean
    fun jsonMapper(): JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
