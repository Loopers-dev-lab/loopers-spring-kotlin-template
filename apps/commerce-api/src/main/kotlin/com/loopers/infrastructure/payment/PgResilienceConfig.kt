package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

/**
 * PG Resilience4j 이벤트 모니터링 설정
 *
 * 블로그 핵심: "서킷브레이커 적용의 운영상 장점"
 * - 상태 전환 모니터링으로 장애 상황 파악
 * - 재시도 로깅으로 일시적 오류 추적
 */
@Configuration
class PgResilienceConfig(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun registerEventListeners() {
        // 결제용 서킷 상태 전환 로깅 (CLOSED ↔ OPEN ↔ HALF_OPEN)
        circuitBreakerRegistry.circuitBreaker("pg-payment").eventPublisher
            .onStateTransition { event ->
                log.warn(
                    "[PG Payment CircuitBreaker] 상태 전환: {} → {}",
                    event.stateTransition.fromState,
                    event.stateTransition.toState,
                )
            }

        // 조회용 서킷 상태 전환 로깅
        circuitBreakerRegistry.circuitBreaker("pg-query").eventPublisher
            .onStateTransition { event ->
                log.warn(
                    "[PG Query CircuitBreaker] 상태 전환: {} → {}",
                    event.stateTransition.fromState,
                    event.stateTransition.toState,
                )
            }

        // 결제용 재시도 발생 시 로깅
        retryRegistry.retry("pg-payment").eventPublisher
            .onRetry { event ->
                log.warn(
                    "[PG Payment Retry] 재시도 #{} - 대기: {}ms, 원인: {}",
                    event.numberOfRetryAttempts,
                    event.waitInterval.toMillis(),
                    event.lastThrowable?.message,
                )
            }

        // 조회용 재시도 발생 시 로깅
        retryRegistry.retry("pg-query").eventPublisher
            .onRetry { event ->
                log.warn(
                    "[PG Query Retry] 재시도 #{} - 대기: {}ms, 원인: {}",
                    event.numberOfRetryAttempts,
                    event.waitInterval.toMillis(),
                    event.lastThrowable?.message,
                )
            }
    }
}
