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
    companion object {
        private val logger = LoggerFactory.getLogger(StockEventHandler::class.java)
    }

    /**
     * 재고 소진 후 처리
     * 트랜잭션 커밋 후 비동기로 처리하여 메인 트랜잭션과 분리
     * 캐시 무효화가 실패해도 재고 차감은 정상적으로 완료됨
     *
     * 처리 순서:
     * 1. 캐시 무효화 (실패해도 계속 진행)
     * 2. Outbox 이벤트 저장 (실패 시 재시도 필요)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleStockDepleted(event: StockDepletedEvent) {
        logger.info(
            "재고 소진 처리 시작: productId=${event.productId}, " +
                    "previousQuantity=${event.previousQuantity}",
        )

        // 1. 캐시 무효화 (실패해도 계속 진행)
        try {
            evictProductCache(event.productId)
        } catch (e: Exception) {
            logger.error("캐시 무효화 실패: productId=${event.productId}", e)
            // 캐시 무효화 실패는 치명적이지 않으므로 계속 진행
        }

        // 2. Outbox 테이블에 이벤트 저장 (Kafka 전송을 위해)
        // OutboxEventPublisher는 @Transactional이므로 자체 트랜잭션 보장
        try {
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
            // Outbox 저장 실패는 치명적이므로 로그를 남기고 재시도 필요
            logger.error(
                "Outbox 이벤트 저장 실패: productId=${event.productId}, " +
                        "이벤트가 Kafka로 발행되지 않을 수 있습니다",
                e,
            )
            // TODO: 실패한 이벤트를 별도 테이블에 저장하거나 알림 발송
            throw e
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
