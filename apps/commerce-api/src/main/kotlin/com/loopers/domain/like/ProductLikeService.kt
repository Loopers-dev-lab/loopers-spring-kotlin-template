package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeService(
    private val productLikeRepository: ProductLikeRepository,
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
        val upsertResult = productLikeRepository.upsert(ProductLike.of(productId, userId))
        if (upsertResult == 1) return ProductResult.AddLike(true)
        return ProductResult.AddLike(false)
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
        val deletedCount = productLikeRepository.deleteByUserIdAndProductId(userId, productId)

        if (deletedCount == 0L) return ProductResult.RemoveLike(false)

        return ProductResult.RemoveLike(true)
    }
}
