package com.loopers.support.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.core.registry.EntryAddedEvent
import io.github.resilience4j.core.registry.EntryRemovedEvent
import io.github.resilience4j.core.registry.EntryReplacedEvent
import io.github.resilience4j.core.registry.RegistryEventConsumer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CircuitBreakerConfig {

    @Bean
    fun circuitBreakerRegistryEventConsumer(): RegistryEventConsumer<CircuitBreaker> {
        return CircuitBreakerRegistryEventConsumer()
    }
}

class CircuitBreakerRegistryEventConsumer : RegistryEventConsumer<CircuitBreaker> {

    private val log = LoggerFactory.getLogger(CircuitBreakerRegistryEventConsumer::class.java)

    override fun onEntryAddedEvent(entryAddedEvent: EntryAddedEvent<CircuitBreaker>) {
        entryAddedEvent.addedEntry.eventPublisher
            .onFailureRateExceeded { event ->
                log.warn(
                    "{} failure rate {}%",
                    event.circuitBreakerName,
                    event.failureRate,
                )
            }
            .onError { event ->
                log.error("{} ERROR!!", event.circuitBreakerName)
            }
            .onStateTransition { event ->
                log.info(
                    "{} state {} -> {}",
                    event.circuitBreakerName,
                    event.stateTransition.fromState,
                    event.stateTransition.toState,
                )
            }
            .onSlowCallRateExceeded { event ->
                log.warn(
                    "{} slow call rate {}%",
                    event.circuitBreakerName,
                    event.slowCallRate,
                )
            }
    }

    override fun onEntryRemovedEvent(entryRemoveEvent: EntryRemovedEvent<CircuitBreaker?>) {
        val circuitBreakerName = entryRemoveEvent.removedEntry?.name ?: "unknown"
        log.debug("{} removed", circuitBreakerName)
    }

    override fun onEntryReplacedEvent(entryReplacedEvent: EntryReplacedEvent<CircuitBreaker?>) {
        val circuitBreakerName = entryReplacedEvent.newEntry?.name ?: "unknown"
        log.debug("{} replaced", circuitBreakerName)
    }
}
