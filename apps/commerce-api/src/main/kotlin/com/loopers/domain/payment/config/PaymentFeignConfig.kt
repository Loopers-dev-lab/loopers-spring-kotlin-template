package com.loopers.domain.payment.config

import feign.RequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentFeignConfig {

    @Value("\${payment.userId}")
    private lateinit var userId: String

    @Bean
    fun userIdInterceptor(): RequestInterceptor {
        return RequestInterceptor { template ->
            template.header("X-USER-ID", userId)
        }
    }
}
