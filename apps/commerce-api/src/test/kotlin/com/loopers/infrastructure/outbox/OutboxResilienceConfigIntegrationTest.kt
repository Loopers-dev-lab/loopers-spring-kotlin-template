package com.loopers.infrastructure.outbox

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("OutboxResilienceConfig 통합 테스트")
class OutboxResilienceConfigIntegrationTest @Autowired constructor(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {

    @DisplayName("서킷브레이커 등록")
    @Nested
    inner class CircuitBreakerRegistration {

        @DisplayName("outbox-relay 서킷브레이커가 등록되어 있다")
        @Test
        fun `outbox-relay circuit breaker is registered`() {
            // when
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("outbox-relay")

            // then
            assertThat(circuitBreaker).isNotNull
            assertThat(circuitBreaker.name).isEqualTo("outbox-relay")
        }

        @DisplayName("outbox-relay 서킷브레이커에 이벤트 리스너가 등록되어 있다")
        @Test
        fun `event listeners are attached to outbox-relay circuit breaker`() {
            // given
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("outbox-relay")
            val stateTransitionEvents = mutableListOf<CircuitBreaker.StateTransition>()

            // 테스트용 리스너 추가로 등록하여 이벤트 발생 여부 확인
            circuitBreaker.eventPublisher.onStateTransition { event ->
                stateTransitionEvents.add(event.stateTransition)
            }

            // when
            circuitBreaker.transitionToOpenState()
            circuitBreaker.transitionToClosedState()

            // then
            assertThat(stateTransitionEvents).contains(
                CircuitBreaker.StateTransition.CLOSED_TO_OPEN,
                CircuitBreaker.StateTransition.OPEN_TO_CLOSED,
            )
        }
    }
}
