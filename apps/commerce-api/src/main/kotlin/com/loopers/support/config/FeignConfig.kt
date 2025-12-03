package com.loopers.support.config

import feign.Request
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class FeignConfig {

    @Bean
    fun feignOptions(): Request.Options {
        return Request.Options(
            3000,
            TimeUnit.MILLISECONDS,
            5000,
            TimeUnit.MILLISECONDS,
            true,
        )
    }
}
