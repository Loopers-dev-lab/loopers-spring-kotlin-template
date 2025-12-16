package com.loopers.domain.stock

import com.loopers.domain.event.EventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 상품 재고 도메인 서비스
 *
 * 품절 처리 및 캐시 무효화 로직을 담당
 */
@Service
class ProductStockService(
    private val productCacheRepository: ProductCacheRepository,
    private val eventService: EventService,
) {
    private val log = LoggerFactory.getLogger(ProductStockService::class.java)

    /**
     * 품절 이벤트 처리
     *
     * 상품 상세 캐시를 삭제하여 다음 조회 시 최신 상태를 반영
     */
    @Transactional
    fun handleSoldOut(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
    ) {
        // 멱등성 체크
        if (eventService.isAlreadyHandled(eventId)) {
            log.debug("이미 처리된 품절 이벤트입니다: eventId={}", eventId)
            return
        }

        log.debug("품절 이벤트 처리: productId={}, eventId={}", productId, eventId)

        // 상품 상세 캐시 삭제 (모든 사용자)
        productCacheRepository.evictProductDetail(productId)

        // 이벤트 처리 기록
        eventService.markAsHandled(eventId, eventType, eventTimestamp)

        log.debug("품절 이벤트 처리 완료: productId={}", productId)
    }
}
