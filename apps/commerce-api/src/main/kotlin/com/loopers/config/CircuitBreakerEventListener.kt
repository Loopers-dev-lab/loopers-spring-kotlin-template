package com.loopers.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CircuitBreakerEventListener(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        // 기존 Circuit Breaker에 리스너 등록
        circuitBreakerRegistry.allCircuitBreakers.forEach { circuitBreaker ->
            registerEventListeners(circuitBreaker)
        }

        // 동적으로 추가되는 Circuit Breaker에도 리스너 등록
        circuitBreakerRegistry.eventPublisher
            .onEntryAdded { event ->
                val addedCircuitBreaker = event.addedEntry
                logger.info("새로운 Circuit Breaker 등록됨: {}", addedCircuitBreaker.name)
                registerEventListeners(addedCircuitBreaker)
            }

        logger.info("Circuit Breaker event listeners initialized")
    }

    private fun registerEventListeners(circuitBreaker: CircuitBreaker) {
        circuitBreaker.eventPublisher
            .onStateTransition { event: CircuitBreakerOnStateTransitionEvent ->
                logger.warn(
                    "Circuit Breaker State Changed! Name: {}, From: {}, To: {}",
                    event.circuitBreakerName,
                    event.stateTransition.fromState,
                    event.stateTransition.toState
                )
            }
            .onError { event ->
                logger.warn(
                    "Circuit Breaker Error: {} - {}",
                    circuitBreaker.name,
                    event.throwable.message,
                    event.throwable
                )
            }
            .onSuccess { event ->
                logger.debug("Circuit Breaker Success: {}", circuitBreaker.name)
            }
            .onCallNotPermitted { event ->
                logger.warn("Circuit Breaker Call Not Permitted: {} - Circuit is OPEN", circuitBreaker.name)
            }
    }
}
