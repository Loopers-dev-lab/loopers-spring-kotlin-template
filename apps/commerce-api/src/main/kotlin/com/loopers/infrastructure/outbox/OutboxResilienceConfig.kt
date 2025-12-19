package com.loopers.infrastructure.outbox

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

/**
 * Outbox Relay 서킷브레이커 이벤트 모니터링 설정
 *
 * Kafka 발행 실패 시 서킷브레이커 상태 변화를 로깅한다.
 */
@Configuration
class OutboxResilienceConfig(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CIRCUIT_BREAKER_NAME = "outbox-relay"
    }

    @PostConstruct
    fun registerEventListeners() {
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME).eventPublisher.apply {
            onStateTransition { event ->
                log.warn(
                    "[{} CB] 상태 전환: {} → {}",
                    CIRCUIT_BREAKER_NAME,
                    event.stateTransition.fromState,
                    event.stateTransition.toState,
                )
            }

            onFailureRateExceeded { event ->
                log.error(
                    "[{} CB] 실패율 임계치 초과: {}% (OPEN 전환 예정)",
                    CIRCUIT_BREAKER_NAME,
                    event.failureRate,
                )
            }
        }
    }
}
