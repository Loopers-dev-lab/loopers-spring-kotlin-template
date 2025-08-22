package com.loopers.infrastructure.pg

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PgFeignTimeoutConfig {
    @Bean
    fun feignOptions(): feign.Request.Options =
        feign.Request.Options(1000, 3000)
}
