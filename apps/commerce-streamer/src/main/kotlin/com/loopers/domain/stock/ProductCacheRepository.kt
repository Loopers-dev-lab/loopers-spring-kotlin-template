package com.loopers.domain.stock

/**
 * 상품 캐시 리포지토리 인터페이스
 *
 * 도메인 계층에서 캐시 관련 작업을 수행하기 위한 인터페이스
 * 실제 구현은 infrastructure 계층에서 담당
 */
interface ProductCacheRepository {
    /**
     * 상품 상세 캐시 삭제 (모든 사용자)
     */
    fun evictProductDetail(productId: Long)
}
