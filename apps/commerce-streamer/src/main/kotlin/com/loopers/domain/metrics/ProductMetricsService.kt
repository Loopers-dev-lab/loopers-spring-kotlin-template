package com.loopers.domain.metrics

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.infrastructure.metrics.ProductMetricsRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * ProductMetrics 비즈니스 로직
 * - 좋아요/조회수/판매량 집계
 * - 이벤트 순서 보장
 */
@Service
class ProductMetricsService(
    private val productMetricsRepository: ProductMetricsRepository,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun incrementLikes(productId: Long, occurredAt: Instant) {
        updateMetrics(productId, occurredAt) { it.incrementLikes() }
    }

    fun decrementLikes(productId: Long, occurredAt: Instant) {
        updateMetrics(productId, occurredAt) { it.decrementLikes() }
    }

    fun incrementViews(productId: Long, occurredAt: Instant) {
        updateMetrics(productId, occurredAt) { it.incrementViews() }
    }

    fun incrementSales(productId: Long, occurredAt: Instant, quantity: Int) {
        updateMetrics(productId, occurredAt) { it.incrementSales(quantity) }
    }

    /**
     * 배치 이벤트 처리 (같은 productId에 대한 여러 이벤트를 한 번에 처리)
     * - 이벤트 순서 보장 (occurredAt 기준)
     * - 같은 productId는 순차 처리
     */
    fun processBatchEvents(productId: Long, events: List<DomainEvent>) {
        val sortedEvents = events.sortedBy { it.occurredAt }

        val metrics = productMetricsRepository.findByProductId(productId)
            ?: ProductMetrics(productId = productId)

        val latestEventTime = sortedEvents.last().occurredAt
        if (metrics.updatedAt.isAfter(latestEventTime)) {
            logger.warn(
                "배치 이벤트 순서 역전 무시: productId=$productId, " +
                "batchLatestTime=$latestEventTime, metricsUpdatedAt=${metrics.updatedAt}"
            )
            return
        }

        sortedEvents.forEach { event ->
            applyEvent(metrics, event)
        }

        productMetricsRepository.save(metrics)

        logger.debug(
            "배치 ProductMetrics 업데이트: productId=$productId, " +
            "eventCount=${events.size}, " +
            "likes=${metrics.likesCount}, views=${metrics.viewCount}, " +
            "sales=${metrics.salesCount}, version=${metrics.version}"
        )
    }

    private fun applyEvent(metrics: ProductMetrics, event: DomainEvent) {
        when (event) {
            is ProductLikedEvent -> metrics.incrementLikes()
            is ProductUnlikedEvent -> metrics.decrementLikes()
            is ProductViewedEvent -> metrics.incrementViews()
            else -> logger.warn("지원하지 않는 이벤트: ${event.eventType}")
        }
    }

    private fun updateMetrics(
        productId: Long,
        eventOccurredAt: Instant,
        update: (ProductMetrics) -> Unit,
    ) {
        val metrics = productMetricsRepository.findByProductId(productId)
            ?: ProductMetrics(productId = productId)

        if (metrics.updatedAt.isAfter(eventOccurredAt)) {
            logger.warn(
                "이벤트 순서 역전 무시: productId={}, eventOccurredAt={}, metrics.updatedAt={}",
                productId, eventOccurredAt, metrics.updatedAt
            )
            return
        }

        update(metrics)
        productMetricsRepository.save(metrics)

        logger.debug(
            "ProductMetrics 업데이트: productId=$productId, " +
                    "likes=${metrics.likesCount}, views=${metrics.viewCount}, " +
                    "sales=${metrics.salesCount}, version=${metrics.version}"
        )
    }
}
