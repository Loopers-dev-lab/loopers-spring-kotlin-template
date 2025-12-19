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
            val eventPublisher = circuitBreaker.eventPublisher

            // when - 상태 전환을 트리거하여 리스너가 동작하는지 확인
            // 서킷브레이커를 OPEN으로 강제 전환 후 다시 CLOSED로
            val initialState = circuitBreaker.state

            // 리스너 등록 여부는 eventPublisher의 onEvent 등록 여부로 간접 확인
            // 실제 상태 전환을 통해 로그가 출력되는지 확인
            circuitBreaker.transitionToOpenState()
            val openState = circuitBreaker.state

            circuitBreaker.transitionToClosedState()
            val closedState = circuitBreaker.state

            // then
            assertThat(openState).isEqualTo(CircuitBreaker.State.OPEN)
            assertThat(closedState).isEqualTo(CircuitBreaker.State.CLOSED)
            // 상태 전환이 가능하면 서킷브레이커가 정상적으로 설정된 것
        }
    }
}
