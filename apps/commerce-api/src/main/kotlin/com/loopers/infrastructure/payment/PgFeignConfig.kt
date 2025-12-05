package com.loopers.infrastructure.payment

import feign.Retryer
import org.springframework.context.annotation.Bean

/**
 * PG Feign 클라이언트 전용 설정
 *
 */
class PgFeignConfig {

    /**
     * Feign 레벨 재시도 비활성화
     *
     * Resilience4j가 재시도를 담당하므로 이중 재시도 방지
     */
    @Bean
    fun pgRetryer(): Retryer = Retryer.NEVER_RETRY
}
