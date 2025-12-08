package com.loopers.interfaces.event

import com.loopers.application.like.event.ProductLikeEvent
import com.loopers.application.product.ProductCache
import com.loopers.domain.like.ProductLikeCount
import com.loopers.domain.like.ProductLikeRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * 좋아요 관련 이벤트를 처리하는 리스너
 *
 * 이벤트 처리 전략:
 * - 좋아요/취소 처리와 집계 업데이트를 분리하여 Eventual Consistency 구현
 * - 좋아요 성공 후 집계 실패하더라도 사용자 경험에는 영향 없음
 * - 집계 불일치는 배치 작업으로 주기적으로 보정 가능
 *
 * 이벤트 처리 흐름:
 * 1. ProductLiked (좋아요 추가)
 *    - AFTER_COMMIT: 좋아요 집계 증가, 캐시 무효화 (비동기)
 *
 * 2. ProductUnliked (좋아요 취소)
 *    - AFTER_COMMIT: 좋아요 집계 감소, 캐시 무효화 (비동기)
 */
@Component
class ProductLikeEventListener(
    private val productLikeRepository: ProductLikeRepository,
    private val productCache: ProductCache,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(ProductLikeEventListener::class.java)

    /**
     * 좋아요 추가 이벤트 처리 - 좋아요 집계 증가
     *
     * AFTER_COMMIT: 좋아요 트랜잭션 커밋 후 집계 처리
     * - 집계 업데이트를 새로운 트랜잭션에서 처리하여 메인 트랜잭션과 분리
     * - 집계 실패해도 좋아요는 이미 성공 상태 (Eventual Consistency)
     * - 캐시 무효화로 다음 조회 시 최신 데이터 반영
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductLiked(event: ProductLikeEvent.ProductLiked) {
        log.info("좋아요 추가 - 집계 증가 시작: productId=${event.productId}, userId=${event.userId}")

        // 좋아요 트랜잭션 커밋 후 새로운 트랜잭션에서 집계 처리
        transactionTemplate.execute {
            try {
                // 집계 증가 (UPDATE product_like_count SET count = count + 1)
                val updatedRows = productLikeRepository.increaseCount(event.productId)

                if (updatedRows == 0) {
                    // 집계 레코드가 없으면 새로 생성 (첫 좋아요인 경우)
                    productLikeRepository.saveCount(ProductLikeCount.create(event.productId, 1L))
                    log.info("좋아요 집계 레코드 생성: productId=${event.productId}")
                }

                log.debug("좋아요 집계 증가 완료: productId=${event.productId}")
            } catch (e: Exception) {
                // 집계 실패는 좋아요 성공에 영향을 주지 않음
                // TODO: 배치 작업으로 집계 재계산 또는 재시도 큐 추가
                log.error("좋아요 집계 증가 실패 (배치로 보정 필요): productId=${event.productId}", e)
            }
        }

        // 캐시 무효화 (다음 조회 시 최신 데이터 반영)
        try {
            productCache.evictProductList()
            productCache.evictLikedProductList(event.userId)
            productCache.evictProductDetail(event.productId)
            log.debug("캐시 무효화 완료: productId=${event.productId}")
        } catch (e: Exception) {
            log.warn("캐시 무효화 실패 (무시 가능): productId=${event.productId}", e)
        }

        log.info("좋아요 추가 후처리 완료: productId=${event.productId}")
    }

    /**
     * 좋아요 취소 이벤트 처리 - 좋아요 집계 감소
     *
     * AFTER_COMMIT: 좋아요 취소 트랜잭션 커밋 후 집계 감소 처리
     * - 집계 업데이트를 새로운 트랜잭션에서 처리
     * - 집계 실패해도 좋아요 취소는 이미 성공 상태
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductUnliked(event: ProductLikeEvent.ProductUnliked) {
        log.info("좋아요 취소 - 집계 감소 시작: productId=${event.productId}, userId=${event.userId}")

        try {
            // 좋아요 취소 트랜잭션 커밋 후 새로운 트랜잭션에서 집계 감소
            transactionTemplate.execute {
                // 집계 감소 (UPDATE product_like_count SET count = count - 1)
                productLikeRepository.decreaseCount(event.productId)
                log.debug("좋아요 집계 감소 완료: productId=${event.productId}")
            }

            // 캐시 무효화
            productCache.evictProductList()
            productCache.evictLikedProductList(event.userId.toString())
            productCache.evictProductDetail(event.productId)
            log.debug("캐시 무효화 완료: productId=${event.productId}")

            log.info("좋아요 취소 후처리 완료: productId=${event.productId}")
        } catch (e: Exception) {
            // 집계 감소 실패
            // TODO: 배치 작업으로 집계 재계산 또는 재시도 큐 추가
            log.error("좋아요 집계 감소 실패 (배치로 보정 필요): productId=${event.productId}", e)
        }
    }
}
