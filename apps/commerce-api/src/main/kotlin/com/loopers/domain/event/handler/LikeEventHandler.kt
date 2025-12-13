package com.loopers.domain.event.handler

import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.event.UserActionType
import com.loopers.domain.product.ProductCacheRepository
import com.loopers.domain.product.ProductLikeCountService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 좋아요 이벤트 핸들러
 * 좋아요 추가/제거 후 집계 처리 및 캐시 무효화
 */
@Component
class LikeEventHandler(
    private val productLikeCountService: ProductLikeCountService,
    private val productCacheRepository: ProductCacheRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)

    /**
     * 좋아요 추가 후 집계 처리
     * 트랜잭션 커밋 후 비동기로 처리하여 메인 트랜잭션과 분리
     * 집계 처리가 실패해도 좋아요는 정상적으로 추가됨
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLikeAdded(event: LikeAddedEvent) {
        try {
            logger.info("좋아요 집계 처리 시작: userId=${event.userId}, productId=${event.productId}")

            // Redis에서 좋아요 수 증가 (atomic 연산)
            productLikeCountService.increment(event.productId)

            // 캐시 무효화
            evictProductCache(event.productId)

            logger.info("좋아요 집계 처리 완료: productId=${event.productId}")

            // 유저 행동 로깅
            eventPublisher.publishEvent(
                UserActionEvent(
                    userId = event.userId,
                    actionType = UserActionType.PRODUCT_LIKE,
                    targetType = "PRODUCT",
                    targetId = event.productId,
                ),
            )
        } catch (e: Exception) {
            // 집계 처리 실패는 좋아요 추가에 영향을 주지 않음
            logger.error("좋아요 집계 처리 실패: productId=${event.productId}", e)
        }
    }

    /**
     * 좋아요 제거 후 집계 처리
     * 트랜잭션 커밋 후 비동기로 처리하여 메인 트랜잭션과 분리
     * 집계 처리가 실패해도 좋아요는 정상적으로 제거됨
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLikeRemoved(event: LikeRemovedEvent) {
        try {
            logger.info("좋아요 제거 집계 처리 시작: userId=${event.userId}, productId=${event.productId}")

            // Redis에서 좋아요 수 감소 (atomic 연산)
            productLikeCountService.decrement(event.productId)

            // 캐시 무효화
            evictProductCache(event.productId)

            logger.info("좋아요 제거 집계 처리 완료: productId=${event.productId}")

            // 유저 행동 로깅
            eventPublisher.publishEvent(
                UserActionEvent(
                    userId = event.userId,
                    actionType = UserActionType.PRODUCT_UNLIKE,
                    targetType = "PRODUCT",
                    targetId = event.productId,
                ),
            )
        } catch (e: Exception) {
            // 집계 처리 실패는 좋아요 제거에 영향을 주지 않음
            logger.error("좋아요 제거 집계 처리 실패: productId=${event.productId}", e)
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
