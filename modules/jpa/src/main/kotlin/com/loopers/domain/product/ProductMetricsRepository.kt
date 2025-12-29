package com.loopers.domain.product

/**
 * Product Metrics Repository
 */
interface ProductMetricsRepository {
    fun save(productMetrics: ProductMetrics): ProductMetrics
    fun findByProductId(productId: Long): ProductMetrics?
    fun findOrCreateByProductId(productId: Long): ProductMetrics

    /**
     * 비관적 락을 사용하여 ProductMetrics 조회 또는 생성
     * Kafka 이벤트 처리 시 동시성 제어를 위해 사용
     */
    fun findOrCreateByProductIdWithLock(productId: Long): ProductMetrics

    /**
     * 여러 상품 ID로 ProductMetrics 조회
     * 일간 랭킹 영구 저장 시 사용
     */
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductMetrics>
}
