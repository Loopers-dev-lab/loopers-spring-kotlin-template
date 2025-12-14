package com.loopers.domain.like

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeService(
    private val productLikeRepository: ProductLikeRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 상품에 좋아요 추가
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        val result = productLikeRepository.save(ProductLike.of(productId, userId))
        if (result == ProductLikeRepository.SaveResult.Created) {
            eventPublisher.publishEvent(
                LikeCreatedEventV1(
                    userId = userId,
                    productId = productId,
                ),
            )
        }
    }

    /**
     * 상품 좋아요 제거
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        val result = productLikeRepository.deleteByUserIdAndProductId(userId, productId)
        if (result == ProductLikeRepository.DeleteResult.Deleted) {
            eventPublisher.publishEvent(
                LikeCanceledEventV1(
                    userId = userId,
                    productId = productId,
                ),
            )
        }
    }
}
