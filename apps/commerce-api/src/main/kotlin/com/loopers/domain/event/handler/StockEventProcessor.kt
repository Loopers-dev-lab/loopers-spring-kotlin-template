package com.loopers.domain.event.handler

import com.loopers.domain.event.StockDepletedEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.product.ProductCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * 재고 이벤트 비동기 처리기
 * @Async와 @Retryable이 정상 작동하도록 별도 Spring Bean으로 분리
 */
@Service
class StockEventProcessor(
    private val productCacheRepository: ProductCacheRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(StockEventProcessor::class.java)
    }

    /**
     * 재고 소진 후 처리를 비동기로 수행
     *
     * 처리 순서:
     * 1. 캐시 무효화 (실패해도 계속 진행)
     * 2. Outbox 이벤트 저장 (실패 시 자동 재시도, 최대 3회)
     *
     * 재시도 정책:
     * - 대상 예외: DataAccessException (DB 연결 오류, 락 타임아웃 등)
     * - 최대 시도: 3회
     * - 백오프: 초기 100ms, 2배씩 증가 (100ms -> 200ms)
     * - 모든 재시도 실패 시: 예외가 @Async 스레드에서 삼켜지므로 로그만 남음
     */
    @Async
    @Retryable(
        retryFor = [DataAccessException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun processStockDepleted(event: StockDepletedEvent) {
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
            // Outbox 저장 실패 시 @Retryable에 의해 자동으로 재시도됨 (최대 3회)
            // 모든 재시도 실패 시 예외를 다시 던져서 AsyncEventFailureHandler로 전달되어
            // 실패한 이벤트를 별도 저장하고 알림을 발송함
            logger.error(
                "Outbox 이벤트 저장 실패 (재시도 포함): productId=${event.productId}, " +
                        "이벤트가 Kafka로 발행되지 않을 수 있습니다",
                e,
            )
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
