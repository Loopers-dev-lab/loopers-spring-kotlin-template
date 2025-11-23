package com.loopers.domain.like

import com.loopers.domain.product.ProductLikeCountService
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val productLikeCountService: ProductLikeCountService,
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
     * Redis의 atomic 연산을 통해 동시성 문제 해결
     */
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        // 이미 존재하는지 확인
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 상품 존재 여부 확인
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        // 저장 시도
        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)

        // Redis에서 좋아요 수 증가 (atomic 연산)
        productLikeCountService.increment(productId)

        // 캐시 무효화
        evictProductCache(productId)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        // 상품 존재 여부 확인
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        // 삭제 시도 및 삭제된 행 수 확인
        val deletedCount = likeRepository.deleteByUserIdAndProductId(userId, productId)

        // 실제로 삭제된 경우에만 Redis 감소 및 캐시 무효화
        if (deletedCount > 0) {
            // Redis에서 좋아요 수 감소 (atomic 연산)
            productLikeCountService.decrement(productId)

            // 캐시 무효화
            evictProductCache(productId)
        }
    }

    private fun evictProductCache(productId: Long) {
        // 상품 상세 캐시 삭제
        val detailCacheKey = "$PRODUCT_DETAIL_CACHE_PREFIX$productId"
        redisTemplate.delete(detailCacheKey)

        // 상품 목록 캐시 삭제 (SCAN을 사용하여 비블로킹 방식으로 패턴 매칭)
        val listCachePattern = "$PRODUCT_LIST_CACHE_PREFIX*"
        val keys = mutableSetOf<String>()

        redisTemplate.execute { connection ->
            val scanOptions = ScanOptions.scanOptions()
                .match(listCachePattern)
                .count(100) // 한 번에 가져올 키 개수 힌트
                .build()

            connection.scan(scanOptions).use { cursor ->
                while (cursor.hasNext()) {
                    keys.add(String(cursor.next()))
                }
            }
        }

        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }
}
