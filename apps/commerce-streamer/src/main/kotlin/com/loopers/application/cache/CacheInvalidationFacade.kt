package com.loopers.application.cache

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.product.StockDecreasedEvent
import com.loopers.domain.cache.ProductCacheService
import com.loopers.infrastructure.event.EventHandledRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 캐시 무효화 이벤트 핸들러
 * - StockDecreasedEvent 수신 시 상품 캐시 무효화
 */
@Component
class CacheInvalidationFacade(
    private val productCacheService: ProductCacheService,
    private val eventHandledRepository: EventHandledRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleEvent(event: DomainEvent) {
        try {
            markAsHandled(event)
        } catch (e: DataIntegrityViolationException) {
            logger.warn("중복 이벤트 무시: eventId=${event.eventId}, eventType=${event.eventType}")
            return
        }

        routeAndProcess(event)
    }

    private fun routeAndProcess(event: DomainEvent) {
        when (event) {
            is StockDecreasedEvent -> {
                logger.info("재고 감소 이벤트 수신: productId=${event.productId}, remainingStock=${event.remainingStock}")
                productCacheService.invalidateProductCache(event.productId)

                // 재고가 소진되었을 경우 목록 캐시도 무효화
                if (event.remainingStock == 0) {
                    logger.info("재고 소진: productId=${event.productId}, 목록 캐시도 무효화")
                    productCacheService.invalidateProductListCache()
                }
            }
            else -> {
                logger.debug("처리 대상 아님: eventType=${event.eventType}")
            }
        }
    }

    private fun markAsHandled(event: DomainEvent) {
        eventHandledRepository.save(
            EventHandled(
                eventId = event.eventId,
                eventType = event.eventType,
                occurredAt = event.occurredAt,
                handledAt = Instant.now()
            )
        )
    }
}
