package com.loopers.domain.like

import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    companion object {
        private const val PRODUCT_DETAIL_CACHE_PREFIX = "product:detail:"
        private const val PRODUCT_LIST_CACHE_PREFIX = "product:list:"
    }

    /**
     * 좋아요를 등록한다
     * UniqueConstraint를 활용하여 멱등성 보장
     * 이미 존재하는 경우 별도 처리 없이 반환 (멱등성)
     * 동시성 경합 상황에서 UniqueConstraint 위반이 발생할 수 있으므로 호출하는 쪽에서 DataIntegrityViolationException 처리 필요
     */
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        // 이미 존재하는지 확인
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 상품 조회 및 좋아요 수 증가
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        product.incrementLikeCount()

        // 저장 시도
        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)

        // 캐시 무효화
        evictProductCache(productId)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        // 좋아요가 존재하는지 확인
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 상품 조회 및 좋아요 수 감소
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        product.decrementLikeCount()

        likeRepository.deleteByUserIdAndProductId(userId, productId)

        // 캐시 무효화
        evictProductCache(productId)
    }

    private fun evictProductCache(productId: Long) {
        // 상품 상세 캐시 삭제
        val detailCacheKey = "$PRODUCT_DETAIL_CACHE_PREFIX$productId"
        redisTemplate.delete(detailCacheKey)

        // 상품 목록 캐시 삭제 (패턴 매칭으로 관련된 모든 목록 캐시 삭제)
        val listCachePattern = "$PRODUCT_LIST_CACHE_PREFIX*"
        val keys = redisTemplate.keys(listCachePattern)
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }
}
