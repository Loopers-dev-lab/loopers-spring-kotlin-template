package com.loopers.application.metrics

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.infrastructure.event.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 배치 메트릭 이벤트 처리 Facade (Application Layer)
 *
 * 역할:
 * 1. 멱등성 일괄 체크 (중복 처리 방지)
 * 2. 처리할 이벤트 필터링
 * 3. 상품별 그룹화
 * 4. 도메인 서비스 호출
 * 5. 처리 완료 기록
 */
@Service
class BatchMetricsEventFacade(
    private val productMetricsService: ProductMetricsService,
    private val eventHandledRepository: EventHandledRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 배치 이벤트 처리 (멱등성 보장 + 그룹화 최적화)
     */
    fun handleBatchEvents(events: List<DomainEvent>) {
        if (events.isEmpty()) {
            return
        }

        logger.info("배치 이벤트 처리 시작: ${events.size}개")

        // 1. 멱등성 일괄 체크
        val eventIds = events.map { it.eventId }
        val handledEventIds = eventHandledRepository.findAllById(eventIds)
            .map { it.eventId }
            .toSet()

        // 2. 처리할 이벤트만 필터링
        val eventsToProcess = events.filter { it.eventId !in handledEventIds }

        if (eventsToProcess.isEmpty()) {
            logger.warn("모든 이벤트가 이미 처리됨 (${events.size}개)")
            return
        }

        // 3. 상품별로 그룹화 (OrderCreatedEvent 제외)
        val (productEvents, orderEvents) = eventsToProcess.partition {
            it is ProductLikedEvent || it is ProductUnlikedEvent || it is ProductViewedEvent
        }

        // 4-1. 상품 이벤트 배치 처리 (상품별로 그룹화)
        if (productEvents.isNotEmpty()) {
            val eventsByProduct = productEvents.groupBy { getProductId(it) }
            eventsByProduct.forEach { (productId, productEvents) ->
                productMetricsService.processBatchEvents(productId, productEvents)
            }
        }

        // 4-2. 주문 이벤트 개별 처리 (OrderCreatedEvent는 여러 상품 포함)
        orderEvents.forEach { event ->
            when (event) {
                is OrderCreatedEvent -> {
                    event.orderItems.forEach { item ->
                        productMetricsService.incrementSales(
                            productId = item.productId,
                            occurredAt = event.occurredAt,
                            quantity = item.quantity
                        )
                    }
                }
                else -> logger.debug("처리 대상 아님: eventType=${event.eventType}")
            }
        }

        // 5. 처리 완료 기록 (배치 insert)
        markAllAsHandled(eventsToProcess)

        logger.info(
            "배치 이벤트 처리 완료: ${eventsToProcess.size}개 " +
            "(중복 제외: ${events.size - eventsToProcess.size}개)"
        )
    }

    private fun getProductId(event: DomainEvent): Long {
        return when (event) {
            is ProductLikedEvent -> event.productId
            is ProductUnlikedEvent -> event.productId
            is ProductViewedEvent -> event.productId
            else -> throw IllegalArgumentException("지원하지 않는 이벤트: ${event.eventType}")
        }
    }

    private fun markAllAsHandled(events: List<DomainEvent>) {
        val handledEvents = events.map { event ->
            EventHandled(
                eventId = event.eventId,
                eventType = event.eventType,
                handledAt = Instant.now()
            )
        }
        eventHandledRepository.saveAll(handledEvents)
    }
}
