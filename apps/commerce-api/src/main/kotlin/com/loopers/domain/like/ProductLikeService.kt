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
     * @return changed=true: 새로 추가됨, changed=false: 이미 존재함
     */
    @Transactional
    fun addLike(userId: Long, productId: Long): ProductResult.AddLike {
        val result = productLikeRepository.save(ProductLike.of(productId, userId))
        return when (result) {
            ProductLikeRepository.SaveResult.AlreadyExists -> ProductResult.AddLike(false)
            ProductLikeRepository.SaveResult.Created -> {
                eventPublisher.publishEvent(
                    LikeCreatedEventV1(
                        userId = userId,
                        productId = productId,
                    ),
                )
                ProductResult.AddLike(true)
            }
        }
    }

    /**
     * 상품 좋아요 제거
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return changed=true: 삭제됨, changed=false: 이미 없음
     */
    @Transactional
    fun removeLike(userId: Long, productId: Long): ProductResult.RemoveLike {
        val result = productLikeRepository.deleteByUserIdAndProductId(userId, productId)
        return when (result) {
            ProductLikeRepository.DeleteResult.NotExist -> ProductResult.RemoveLike(false)
            ProductLikeRepository.DeleteResult.Deleted -> {
                eventPublisher.publishEvent(
                    LikeCanceledEventV1(
                        userId = userId,
                        productId = productId,
                    ),
                )
                ProductResult.RemoveLike(true)
            }
        }
    }
}
