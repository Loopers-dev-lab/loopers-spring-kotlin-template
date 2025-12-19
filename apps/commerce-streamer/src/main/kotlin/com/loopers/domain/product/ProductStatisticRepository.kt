package com.loopers.domain.product

/**
 * ProductStatistic Repository 인터페이스
 *
 * - 상품 통계 조회 및 배치 저장
 * - infrastructure/product에서 구현
 */
interface ProductStatisticRepository {
    fun findByProductId(productId: Long): ProductStatistic?
    fun findAllByProductIds(productIds: List<Long>): List<ProductStatistic>
    fun saveAll(statistics: List<ProductStatistic>): List<ProductStatistic>
}
