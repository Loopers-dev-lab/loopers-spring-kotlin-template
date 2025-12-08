package com.loopers.application.like

import com.loopers.application.like.event.ProductLikeEvent
import com.loopers.application.user.event.UserActivityEvent
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 좋아요 파사드
 *
 * 책임:
 * - 좋아요/취소 비즈니스 로직 조율
 * - 상품/사용자 존재 여부 검증
 * - 좋아요 저장/삭제 후 이벤트 발행
 *
 * 이벤트 발행 전략:
 * - ProductLikeEvent: 집계 업데이트 및 캐시 무효화 (AFTER_COMMIT, 비동기)
 * - UserActivityEvent: 사용자 활동 로깅 (즉시, 비동기)
 */
@Component
class ProductLikeFacade(
    private val productLikeService: ProductLikeService,
    private val productService: ProductService,
    private val userService: UserService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(ProductLikeFacade::class.java)

    @Transactional
    fun like(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)

        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        // 좋아요 저장 (집계는 이벤트 리스너에서 처리)
        try {
            // 좋아요만 저장하고 집계는 하지 않음
            val like = productLikeService.getBy(productId, user.id)
            if (like == null) {
                // 새로운 좋아요만 저장
                productLikeService.like(product, user)

                // 좋아요 이벤트 발행
                // ProductLikeEventListener에서 집계 처리를 비동기로 수행
                // 집계가 실패해도 좋아요는 이미 저장된 상태 (eventual consistency)
                applicationEventPublisher.publishEvent(
                    ProductLikeEvent.ProductLiked(
                        productId = product.id,
                        userId = userId,
                    ),
                )
            }
        } catch (e: DataIntegrityViolationException) {
            // 이미 좋아요가 존재하는 경우 - 무시 (멱등성)
            log.debug("중복 좋아요 시도 무시: productId=${product.id}, userId=${user.id}")
        }

        // 사용자 활동 로깅 이벤트 발행
        applicationEventPublisher.publishEvent(
            UserActivityEvent.UserActivity(
                userId = user.id,
                activityType = UserActivityEvent.ActivityType.PRODUCT_LIKE,
                targetId = productId,
            ),
        )
    }

    /**
     * 상품 좋아요 취소
     *
     * 처리 흐름:
     * 1. 사용자, 상품 존재 여부 검증
     * 2. 좋아요 존재 여부 체크
     * 3. 좋아요 삭제
     * 4. 이벤트 발행 (집계 감소, 사용자 활동 로깅)
     *
     * 멱등성: 이미 취소된 좋아요는 무시하고 성공 응답
     */
    @Transactional
    fun unlike(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)
        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        // 좋아요 삭제 (집계 감소는 이벤트 리스너에서 처리)
        val like = productLikeService.getBy(productId, user.id)
        if (like != null) {
            productLikeService.unlike(product, user)

            // 좋아요 취소 이벤트 발행
            // ProductLikeEventListener에서 집계 감소를 비동기로 수행
            applicationEventPublisher.publishEvent(
                ProductLikeEvent.ProductUnliked(
                    productId = product.id,
                    userId = userId,
                ),
            )

            // 사용자 활동 로깅 이벤트 발행
            applicationEventPublisher.publishEvent(
                UserActivityEvent.UserActivity(
                    userId = user.id,
                    activityType = UserActivityEvent.ActivityType.PRODUCT_UNLIKE,
                    targetId = productId,
                ),
            )
        }
    }
}
