package com.loopers.application.cache

interface ProductCache {
    /**
     * 상품 상세 캐시 삭제 (모든 사용자)
     */
    fun evictProductDetail(productId: Long)
}
