package com.loopers.infrastructure.payment

import feign.RequestInterceptor
import feign.Retryer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean

/**
 * PG Feign 클라이언트 전용 설정
 */
class PgFeignConfig(
    @Value("\${pg.merchant-id}")
    private val merchantId: String,
) {

    /**
     * Feign 레벨 재시도 비활성화
     *
     * Resilience4j가 재시도를 담당하므로 이중 재시도 방지
     */
    @Bean
    fun pgRetryer(): Retryer = Retryer.NEVER_RETRY

    /**
     * PG 서버 인증을 위한 가맹점 ID 헤더 추가
     */
    @Bean
    fun pgAuthInterceptor(): RequestInterceptor = RequestInterceptor { template ->
        template.header("X-USER-ID", merchantId)
    }
}
