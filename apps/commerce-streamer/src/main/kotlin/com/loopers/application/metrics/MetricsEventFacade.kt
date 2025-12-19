package com.loopers.application.metrics

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.infrastructure.event.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MetricsEventFacade(
    private val productMetricsService: ProductMetricsService,
    private val eventHandledRepository: EventHandledRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleEvent(event: DomainEvent) {
        if (isAlreadyHandled(event)) {
            logger.warn("중복 이벤트 무시: eventId=${event.eventId}, eventType=${event.eventType}")
            return
        }

        routeAndProcess(event)
        markAsHandled(event)
    }

    private fun routeAndProcess(event: DomainEvent) {
        when (event) {
            is ProductLikedEvent -> {
                productMetricsService.incrementLikes(event.productId, event.occurredAt)
            }
            is ProductUnlikedEvent -> {
                productMetricsService.decrementLikes(event.productId, event.occurredAt)
            }
            is ProductViewedEvent -> {
                productMetricsService.incrementViews(event.productId, event.occurredAt)
            }
            is OrderCreatedEvent -> {
                event.orderItems.forEach { item ->
                    productMetricsService.incrementSales(
                        productId = item.productId,
                        occurredAt = event.occurredAt,
                        quantity = item.quantity
                    )
                }
            }
            else -> {
                logger.debug("처리 대상 아님: eventType=${event.eventType}")
            }
        }
    }

    private fun isAlreadyHandled(event: DomainEvent) : Boolean {
        return eventHandledRepository.existsByEventId(event.eventId)
    }

    private fun markAsHandled(event: DomainEvent) {
        eventHandledRepository.save(
            EventHandled(
                eventId = event.eventId,
                eventType = event.eventType,
                handledAt = Instant.now()
            )
        )
    }
}
