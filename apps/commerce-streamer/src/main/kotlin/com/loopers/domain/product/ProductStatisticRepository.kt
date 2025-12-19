package com.loopers.domain.product

/**
 * ProductStatistic Repository 인터페이스
 *
 * - 상품 통계 조회 및 카운트 업데이트
 * - infrastructure/product에서 구현
 */
interface ProductStatisticRepository {
    fun findByProductId(productId: Long): ProductStatistic?
    fun incrementLikeCount(productId: Long)
    fun decrementLikeCount(productId: Long)
    fun incrementSalesCount(productId: Long, amount: Int)
    fun incrementViewCount(productId: Long)
}
