package com.loopers.domain.like

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

open class ProductLikeService(
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
    open fun addLike(userId: Long, productId: Long): ProductResult.AddLike {
        runCatching {
            productLikeRepository.save(ProductLike.create(productId, userId))
        }.onFailure { exception ->
            when (exception) {
                is DataIntegrityViolationException ->
                    return ProductResult.AddLike(false)

                else -> throw exception
            }
        }

        return ProductResult.AddLike(true)
    }

    /**
     * 상품 좋아요 제거
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return changed=true: 삭제됨, changed=false: 이미 없음
     */
    @Transactional
    open fun removeLike(userId: Long, productId: Long): ProductResult.RemoveLike {
        val deletedCount = productLikeRepository.deleteByUserIdAndProductId(userId, productId)

        if (deletedCount == 0L) return ProductResult.RemoveLike(false)

        return ProductResult.RemoveLike(true)
    }
}
