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
        listOf("pg-payment", "pg-query").forEach { name ->
            circuitBreakerRegistry.circuitBreaker(name).eventPublisher.apply {
                // 상태 전환
                onStateTransition { event ->
                    log.warn(
                        "[{} CB] 상태 전환: {} → {}",
                        name,
                        event.stateTransition.fromState,
                        event.stateTransition.toState,
                    )
                }

                // 실패율 임계치 초과 - "왜" OPEN 됐는지 알 수 있음
                onFailureRateExceeded { event ->
                    log.error(
                        "[{} CB] 실패율 임계치 초과: {}% (OPEN 전환 예정)",
                        name,
                        event.failureRate,
                    )
                }

                // 느린 호출 비율 임계치 초과 - slowCallRateThreshold 설정 시
                onSlowCallRateExceeded { event ->
                    log.error(
                        "[{} CB] 느린 호출 비율 초과: {}% (OPEN 전환 예정)",
                        name,
                        event.slowCallRate,
                    )
                }
            }

            retryRegistry.retry(name).eventPublisher.apply {
                onRetry { event ->
                    log.warn(
                        "[{} Retry] 재시도 #{} - 원인: {}",
                        name,
                        event.numberOfRetryAttempts,
                        event.lastThrowable?.javaClass?.simpleName,
                    )
                }
            }
        }
    }
}
