package com.loopers.domain.event.handler

import com.loopers.domain.event.StockDepletedEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.product.ProductCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 재고 이벤트 핸들러
 * 재고 소진 후 캐시 무효화 및 이벤트 발행
 */
@Component
class StockEventHandler(
    private val productCacheRepository: ProductCacheRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(StockEventHandler::class.java)

    /**
     * 재고 소진 후 처리
     * 트랜잭션 커밋 후 비동기로 처리하여 메인 트랜잭션과 분리
     * 캐시 무효화가 실패해도 재고 차감은 정상적으로 완료됨
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleStockDepleted(event: StockDepletedEvent) {
        try {
            logger.info(
                "재고 소진 처리 시작: productId=${event.productId}, " +
                    "previousQuantity=${event.previousQuantity}",
            )

            // 캐시 무효화
            evictProductCache(event.productId)

            // Outbox 테이블에 이벤트 저장 (Kafka 전송을 위해)
            outboxEventPublisher.publish(
                eventType = "StockDepletedEvent",
                topic = "catalog-events",
                partitionKey = event.productId.toString(),
                payload = event,
                aggregateType = "Product",
                aggregateId = event.productId,
            )

            logger.info("재고 소진 처리 완료: productId=${event.productId}")
        } catch (e: Exception) {
            // 캐시 무효화 실패는 재고 차감에 영향을 주지 않음
            logger.error("재고 소진 처리 실패: productId=${event.productId}", e)
        }
    }

    private fun evictProductCache(productId: Long) {
        try {
            // 상품 상세 캐시 삭제
            val detailCacheKey = productCacheRepository.buildProductDetailCacheKey(productId)
            productCacheRepository.delete(detailCacheKey)

            // 상품 목록 캐시 삭제
            val listCachePattern = productCacheRepository.getProductListCachePattern()
            productCacheRepository.deleteByPattern(listCachePattern)

            logger.debug("캐시 무효화 완료: productId=$productId")
        } catch (e: Exception) {
            logger.warn("캐시 무효화 실패: productId=$productId", e)
        }
    }
}
