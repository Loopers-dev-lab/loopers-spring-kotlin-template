package com.loopers.domain.event.handler

import com.loopers.domain.event.StockDepletedEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.product.ProductCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.dao.TransientDataAccessException
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
     * - 대상 예외:
     *   - TransientDataAccessException: DB 연결 오류, 락 타임아웃, 일시적 네트워크 문제 등
     *   - RecoverableDataAccessException: 복구 가능한 데이터 액세스 오류
     *   - 제외: 제약 조건 위반, 무결성 오류 등 영구적 오류 (재시도 불필요)
     * - 최대 시도: 3회
     * - 백오프: 초기 100ms, 2배씩 증가 (100ms -> 200ms)
     * - 모든 재시도 실패 시: AsyncEventFailureHandler가 처리하여 실패 이벤트 저장 및 알림 발송
     */
    @Async
    @Retryable(
        retryFor = [TransientDataAccessException::class, RecoverableDataAccessException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun processStockDepleted(event: StockDepletedEvent) {
        logger.info(
            "재고 소진 처리 시작: productId=${event.productId}, " +
                    "previousQuantity=${event.previousQuantity}",
        )

        // 1. 캐시 무효화 (실패해도 계속 진행 - 내부에서 예외 처리됨)
        evictProductCache(event.productId)

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
            // Outbox 저장 실패 시 일시적/복구 가능 오류인 경우 @Retryable에 의해 자동 재시도 (최대 3회)
            // 영구적 오류(제약 조건 위반 등)는 재시도하지 않고 즉시 실패 처리
            // 모든 재시도 실패 시 예외를 다시 던져서 AsyncEventFailureHandler로 전달되어
            // 실패한 이벤트를 별도 저장하고 알림을 발송함
            logger.error(
                "Outbox 이벤트 저장 실패: productId=${event.productId}, " +
                        "exceptionType=${e::class.simpleName}, " +
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
