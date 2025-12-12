package com.loopers.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CircuitBreakerEventListener(
    private val circuitBreakerRegistry: CircuitBreakerRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { circuitBreaker ->
            circuitBreaker.eventPublisher
                .onStateTransition { event: CircuitBreakerOnStateTransitionEvent ->
                    logger.warn(
                        """
                        ========================================
                        Circuit Breaker State Changed!
                        Name: ${event.circuitBreakerName}
                        From: ${event.stateTransition.fromState}
                        To: ${event.stateTransition.toState}
                        ========================================
                        """.trimIndent()
                    )
                }
                .onError { event ->
                    logger.debug("Circuit Breaker Error: ${circuitBreaker.name} - ${event.throwable.message}")
                }
                .onSuccess { event ->
                    logger.debug("Circuit Breaker Success: ${circuitBreaker.name}")
                }
                .onCallNotPermitted { event ->
                    logger.warn("Circuit Breaker Call Not Permitted: ${circuitBreaker.name} - Circuit is OPEN")
                }
        }
        
        logger.info("Circuit Breaker event listeners initialized")
    }
}

